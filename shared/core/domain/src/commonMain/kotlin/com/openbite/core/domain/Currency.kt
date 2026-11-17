package com.openbite.core.domain

import com.openbite.core.data.PlaceRepository
import com.openbite.core.data.platform.CountryGeocoder
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.model.CurrencyChoice
import com.openbite.core.model.CurrencySource
import com.openbite.core.model.Place
import com.openbite.core.model.currencyForCountry
import com.openbite.core.model.currentRegion
import com.openbite.core.model.isKnownCurrency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 手机地区（ISO 3166 两位字母）。测试里换成固定值。 */
fun interface PhoneRegionProvider {
    fun region(): String?
}

class SystemPhoneRegion : PhoneRegionProvider {
    override fun region(): String? = currentRegion()
}

/** 连手机地区都推不出币种时的最后兜底。 */
const val LAST_RESORT_CURRENCY = "USD"

/** 合法币种代码转大写；不认识返回 null。 */
private fun String?.knownCurrency(): String? = this?.trim()?.uppercase()?.takeIf(::isKnownCurrency)

/**
 * 店铺所在国家/地区：已经存过就直接用；没存过但有经纬度就反查一次，查到了存起来。
 * 查不到（没网、没有地理编码服务）返回 null，下次用到再查。
 */
class ResolvePlaceCountryUseCase(private val places: PlaceRepository, private val geocoder: CountryGeocoder) {
    suspend operator fun invoke(place: Place): String? {
        place.countryCode?.let { return it }
        val lat = place.lat ?: return null
        val lon = place.lon ?: return null
        val country = lookup(lat, lon) ?: return null
        runCatching { places.setCountry(place.id, lat, lon, country) }
        return country
    }

    /** 只反查，不存（新店还没建好时用）。 */
    suspend fun lookup(lat: Double, lon: Double): String? = try {
        geocoder.countryCode(lat, lon)?.trim()?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/**
 * 需要一个默认币种时都走这里，按顺序取第一个有的：
 * 1. 用户在这道菜 / 这张小票上自己选的（[manual]）；
 * 2. 小票上读出来的（[receipt]）；
 * 3. 菜单上写的（[menu]：识别抬头或点的菜的价格）；
 * 4. 这家店已有的菜用得最多的币种；
 * 5. 店铺位置所在国家/地区的币种（[country] 直接给，或按店铺经纬度反查）；
 * 6. 设置里的“备用币种”；
 * 7. 手机地区的币种。
 */
class DefaultCurrencyUseCase(
    private val places: PlaceRepository,
    private val countries: ResolvePlaceCountryUseCase,
    private val preferences: PreferencesStore,
    private val region: PhoneRegionProvider,
) {
    suspend operator fun invoke(
        placeId: String?,
        manual: String? = null,
        receipt: String? = null,
        menu: String? = null,
        country: String? = null,
    ): CurrencyChoice {
        manual.knownCurrency()?.let { return CurrencyChoice(it, CurrencySource.MANUAL) }
        receipt.knownCurrency()?.let { return CurrencyChoice(it, CurrencySource.RECEIPT) }
        menu.knownCurrency()?.let { return CurrencyChoice(it, CurrencySource.MENU) }
        val place = placeId?.let { places.observePlace(it).first() }
        if (placeId != null) {
            val used = places.observeMenu(placeId).first().mapNotNull { it.price?.currency.knownCurrency() }
            used.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.let { return CurrencyChoice(it, CurrencySource.PLACE) }
        }
        val placeCountry = country ?: place?.let { countries(it) }
        placeCountry?.let(::currencyForCountry)?.let { return CurrencyChoice(it, CurrencySource.PLACE_COUNTRY) }
        return fallback()
    }

    /** 设置里的备用币种，没设就按手机地区。 */
    suspend fun fallback(): CurrencyChoice {
        preferences.observeFallbackCurrency().first().knownCurrency()?.let { return CurrencyChoice(it, CurrencySource.SETTINGS) }
        return regionChoice()
    }

    /** 手机地区的币种（设置页“跟随地区 · CNY”）。 */
    fun regionChoice(): CurrencyChoice =
        CurrencyChoice(region.region()?.let(::currencyForCountry) ?: LAST_RESORT_CURRENCY, CurrencySource.PHONE_REGION)
}

/** 币种的两个设置：备用币种、最近用过。 */
class CurrencyPreferencesUseCase(private val preferences: PreferencesStore) {
    fun fallback(): Flow<String?> = preferences.observeFallbackCurrency()

    /** null：跟随地区。不认识的代码不存。 */
    suspend fun setFallback(code: String?) {
        if (code == null) preferences.setFallbackCurrency(null) else code.knownCurrency()?.let { preferences.setFallbackCurrency(it) }
    }

    fun recent(): Flow<List<String>> = preferences.observeRecentCurrencies()

    suspend fun remember(code: String) {
        code.knownCurrency()?.let { preferences.addRecentCurrency(it) }
    }
}

/** 改店铺位置（地图上选点）。改完在应用级作用域里重新反查国家/地区，不让界面等。 */
class SetPlaceLocationUseCase(
    private val places: PlaceRepository,
    private val countries: ResolvePlaceCountryUseCase,
    private val appScope: ApplicationScope,
) {
    /** 经纬度超出范围返回 false。 */
    suspend operator fun invoke(placeId: String, lat: Double, lon: Double): Boolean {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return false
        places.setLocation(placeId, lat, lon)
        appScope.launch {
            val place = places.observePlace(placeId).first() ?: return@launch
            countries(place)
        }
        return true
    }
}

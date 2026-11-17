package com.openbite.core.domain

import com.openbite.core.model.CurrencyChoice
import com.openbite.core.model.CurrencySource
import com.openbite.core.model.Dish
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.testing.FakeGeocoder
import com.openbite.core.testing.FakePlaces
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.testAppScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrencyTest {
    private val kyoto = 35.0 to 135.7
    private val paris = 48.86 to 2.35

    private val place = Place("p", "おばんざい 小春", kyoto.first, kyoto.second, null)
    private val prefs = FakePreferences()
    private val geocoder = FakeGeocoder(mapOf(kyoto to "JP", paris to "FR"))

    private fun dish(id: String, price: Money?) = Dish(id, "p", "", id, id, price)

    private fun useCase(places: FakePlaces, region: String? = "CN") =
        DefaultCurrencyUseCase(places, ResolvePlaceCountryUseCase(places, geocoder), prefs, PhoneRegionProvider { region })

    @Test
    fun manualThenReceiptThenMenuWin() = runTest {
        val pick = useCase(FakePlaces(listOf(place), mapOf("p" to listOf(dish("a", Money(100, "USD"))))))
        assertEquals(CurrencyChoice("EUR", CurrencySource.MANUAL), pick("p", manual = "eur", receipt = "GBP", menu = "THB"))
        assertEquals(CurrencyChoice("GBP", CurrencySource.RECEIPT), pick("p", receipt = "GBP", menu = "THB"))
        assertEquals(CurrencyChoice("THB", CurrencySource.MENU), pick("p", menu = "THB"))
        // 不认识的代码跳过。
        assertEquals(CurrencyChoice("THB", CurrencySource.MENU), pick("p", manual = "ABC", menu = "THB"))
        assertTrue(geocoder.calls.isEmpty(), "前面有结果就不反查")
    }

    @Test
    fun placeDishesUseMostCommonCurrency() = runTest {
        val menu = listOf(dish("a", Money(1, "JPY")), dish("b", Money(2, "USD")), dish("c", Money(3, "JPY")), dish("d", null))
        val pick = useCase(FakePlaces(listOf(place), mapOf("p" to menu)))
        assertEquals(CurrencyChoice("JPY", CurrencySource.PLACE), pick("p"))
    }

    @Test
    fun placeCountryIsLookedUpOnceAndStored() = runTest {
        val places = FakePlaces(listOf(place))
        val pick = useCase(places)
        assertEquals(CurrencyChoice("JPY", CurrencySource.PLACE_COUNTRY), pick("p"))
        assertEquals("JP", places.places.value.single().countryCode)
        pick("p")
        assertEquals(1, geocoder.calls.size, "存过就不再查")
    }

    @Test
    fun offlineFallsThroughAndRetriesLater() = runTest {
        val places = FakePlaces(listOf(place))
        val pick = useCase(places)
        geocoder.offline = true
        assertEquals(CurrencyChoice("CNY", CurrencySource.PHONE_REGION), pick("p"))
        assertNull(places.places.value.single().countryCode)

        prefs.fallbackCurrency.value = "EUR"
        assertEquals(CurrencyChoice("EUR", CurrencySource.SETTINGS), pick("p"))

        geocoder.offline = false
        assertEquals(CurrencyChoice("JPY", CurrencySource.PLACE_COUNTRY), pick("p"), "联网后再查")
        assertEquals(3, geocoder.calls.size, "离线两次 + 联网一次")
    }

    @Test
    fun newPlaceCountryAndRegionFallbacks() = runTest {
        val pick = useCase(FakePlaces())
        assertEquals(CurrencyChoice("EUR", CurrencySource.PLACE_COUNTRY), pick(null, country = "FR"))
        assertEquals(CurrencyChoice("CNY", CurrencySource.PHONE_REGION), pick(null))
        assertEquals(CurrencyChoice(LAST_RESORT_CURRENCY, CurrencySource.PHONE_REGION), useCase(FakePlaces(), region = null)(null))
    }

    @Test
    fun movingPlaceClearsCountryAndLooksUpAgain() = runTest {
        val places = FakePlaces(listOf(place.copy(countryCode = "JP")))
        val setLocation = SetPlaceLocationUseCase(places, ResolvePlaceCountryUseCase(places, geocoder), testAppScope(this))
        assertTrue(setLocation("p", paris.first, paris.second))
        advanceUntilIdle()
        assertEquals("FR", places.places.value.single().countryCode)
        assertFalse(setLocation("p", 91.0, 0.0))
    }

    @Test
    fun staleLookupDoesNotOverwriteNewLocation() = runTest {
        val places = FakePlaces(listOf(place))
        places.setLocation("p", paris.first, paris.second)
        // 按旧位置查出来的结果不能写到新位置上。
        places.setCountry("p", kyoto.first, kyoto.second, "JP")
        assertNull(places.places.value.single().countryCode)
    }

    @Test
    fun currencyPreferences() = runTest {
        val currencyPrefs = CurrencyPreferencesUseCase(prefs)
        listOf("JPY", "EUR", "jpy", "ABC", "USD", "GBP", "THB", "KRW").forEach { currencyPrefs.remember(it) }
        assertEquals(listOf("KRW", "THB", "GBP", "USD", "JPY"), currencyPrefs.recent().first())
        currencyPrefs.setFallback("ABC")
        assertNull(currencyPrefs.fallback().first(), "不认识的不存")
        currencyPrefs.setFallback("chf")
        assertEquals("CHF", currencyPrefs.fallback().first())
        currencyPrefs.setFallback(null)
        assertNull(currencyPrefs.fallback().first())
    }
}

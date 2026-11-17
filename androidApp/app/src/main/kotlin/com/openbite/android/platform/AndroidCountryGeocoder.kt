package com.openbite.android.platform

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.openbite.core.data.platform.CountryGeocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 系统 Geocoder 反查国家/地区代码。没有地理编码服务、没网、出错或 [TIMEOUT_MS] 内没结果都返回 null，
 * 共享代码会在下次需要默认币种时再查。只取 countryCode，不存地址。
 */
internal class AndroidCountryGeocoder(context: Context) : CountryGeocoder {
    private val context = context.applicationContext

    override suspend fun countryCode(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        // 国家代码和语言无关，固定用英文，避免界面语言影响结果。
        val geocoder = Geocoder(context, Locale.ENGLISH)
        return withTimeoutOrNull(TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        lat,
                        lon,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull()?.countryCode)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.countryCode }.getOrNull()
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}

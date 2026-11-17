package com.openbite.core.model

import android.content.res.Resources
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private fun locale(tag: String): Locale = Locale.forLanguageTag(tag)

private fun currencyOf(code: String): Currency? = runCatching { Currency.getInstance(code.uppercase()) }.getOrNull()

actual fun currentLocaleTag(): String = Locale.getDefault().toLanguageTag()

actual fun currentRegion(): String? {
    // App 单独设了语言时 Locale.getDefault() 跟着变（可能没有地区），地区以系统设置为准。
    // JVM 单测里没有 Android 运行时，退回默认 Locale。
    val system = runCatching { Resources.getSystem().configuration.locales[0]?.country }.getOrNull()
    return (system?.takeIf { it.isNotEmpty() } ?: Locale.getDefault().country).uppercase().takeIf { it.length == 2 }
}

actual fun currencyFractionDigits(code: String): Int? = currencyOf(code)?.defaultFractionDigits?.takeIf { it >= 0 }

actual fun currencyDisplayName(code: String, localeTag: String): String =
    currencyOf(code)?.getDisplayName(locale(localeTag)) ?: code.uppercase()

actual fun currencySymbol(code: String, localeTag: String): String =
    currencyOf(code)?.getSymbol(locale(localeTag)) ?: code.uppercase()

actual fun availableCurrencyCodes(): List<String> =
    Locale.getISOCountries().mapNotNull(::currencyForCountry).distinct().sorted()

actual fun currencyForCountry(countryCode: String): String? = runCatching {
    Currency.getInstance(Locale.Builder().setRegion(countryCode.uppercase()).build())?.currencyCode
}.getOrNull()?.takeIf { currencyFractionDigits(it) != null }

actual fun languageDisplayName(languageTag: String, inLocaleTag: String): String {
    val target = locale(inLocaleTag)
    val name = locale(languageTag).getDisplayName(target).ifBlank { languageTag }
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(target) else it.toString() }
}

internal actual fun formatCurrencyAmount(amountMinor: Long, code: String, fractionDigits: Int, localeTag: String): String {
    val format = NumberFormat.getCurrencyInstance(locale(localeTag))
    format.currency = Currency.getInstance(code.uppercase())
    format.minimumFractionDigits = fractionDigits
    format.maximumFractionDigits = fractionDigits
    return format.format(BigDecimal.valueOf(amountMinor, fractionDigits))
}

internal actual fun formatPlainNumber(amountMinor: Long, fractionDigits: Int, localeTag: String): String {
    val format = NumberFormat.getNumberInstance(locale(localeTag))
    format.minimumFractionDigits = fractionDigits
    format.maximumFractionDigits = fractionDigits
    format.isGroupingUsed = true
    return format.format(BigDecimal.valueOf(amountMinor, fractionDigits))
}

internal actual fun decimalSeparator(localeTag: String): Char = DecimalFormatSymbols.getInstance(locale(localeTag)).decimalSeparator

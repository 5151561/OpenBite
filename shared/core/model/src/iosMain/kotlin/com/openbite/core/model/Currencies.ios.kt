package com.openbite.core.model

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.ISOCountryCodes
import platform.Foundation.ISOCurrencyCodes
import platform.Foundation.commonISOCurrencyCodes
import platform.Foundation.currentLocale
import platform.Foundation.preferredLanguages

// iOS：NSLocale / NSNumberFormatter。openbite.ios=false 时不编译，保持和 Android 同样的行为约定。

private fun locale(tag: String): NSLocale = NSLocale(localeIdentifier = tag.replace('-', '_'))

private fun isKnownCurrency(code: String): Boolean = NSLocale.ISOCurrencyCodes.contains(code.uppercase())

private fun decimal(amountMinor: Long, fractionDigits: Int): NSDecimalNumber {
    val negative = amountMinor < 0
    val abs = if (negative) -amountMinor else amountMinor
    return NSDecimalNumber(mantissa = abs.toULong(), exponent = (-fractionDigits).toShort(), isNegative = negative)
}

actual fun currentLocaleTag(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String) ?: NSLocale.currentLocale.localeIdentifier.replace('_', '-')

actual fun currentRegion(): String? =
    (NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String)?.uppercase()?.takeIf { it.length == 2 }

actual fun currencyFractionDigits(code: String): Int? {
    if (!isKnownCurrency(code)) return null
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    formatter.currencyCode = code.uppercase()
    return formatter.maximumFractionDigits.toInt()
}

actual fun currencyDisplayName(code: String, localeTag: String): String =
    locale(localeTag).displayNameForKey(NSLocaleCurrencyCode, code.uppercase()) ?: code.uppercase()

actual fun currencySymbol(code: String, localeTag: String): String {
    if (!isKnownCurrency(code)) return code.uppercase()
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    formatter.locale = locale(localeTag)
    formatter.currencyCode = code.uppercase()
    return formatter.currencySymbol
}

actual fun availableCurrencyCodes(): List<String> =
    NSLocale.ISOCountryCodes.mapNotNull { (it as? String)?.let(::currencyForCountry) }
        .ifEmpty { NSLocale.commonISOCurrencyCodes.mapNotNull { it as? String } }
        .distinct()
        .sorted()

actual fun currencyForCountry(countryCode: String): String? {
    val identifier = NSLocale.localeIdentifierFromComponents(mapOf(NSLocaleCountryCode to countryCode.uppercase()))
    return (NSLocale(localeIdentifier = identifier).objectForKey(NSLocaleCurrencyCode) as? String)
        ?.takeIf { currencyFractionDigits(it) != null }
}

actual fun languageDisplayName(languageTag: String, inLocaleTag: String): String {
    val name = locale(inLocaleTag).displayNameForKey(NSLocaleIdentifier, languageTag.replace('-', '_')) ?: languageTag
    return name.replaceFirstChar { it.uppercaseChar() }
}

internal actual fun formatCurrencyAmount(amountMinor: Long, code: String, fractionDigits: Int, localeTag: String): String {
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    formatter.locale = locale(localeTag)
    formatter.currencyCode = code.uppercase()
    formatter.minimumFractionDigits = fractionDigits.toULong()
    formatter.maximumFractionDigits = fractionDigits.toULong()
    return formatter.stringFromNumber(decimal(amountMinor, fractionDigits)) ?: "${code.uppercase()} $amountMinor"
}

internal actual fun formatPlainNumber(amountMinor: Long, fractionDigits: Int, localeTag: String): String {
    val formatter = NSNumberFormatter()
    formatter.numberStyle = NSNumberFormatterDecimalStyle
    formatter.locale = locale(localeTag)
    formatter.usesGroupingSeparator = true
    formatter.minimumFractionDigits = fractionDigits.toULong()
    formatter.maximumFractionDigits = fractionDigits.toULong()
    return formatter.stringFromNumber(decimal(amountMinor, fractionDigits)) ?: amountMinor.toString()
}

internal actual fun decimalSeparator(localeTag: String): Char =
    (locale(localeTag).objectForKey(platform.Foundation.NSLocaleDecimalSeparator) as? String)?.firstOrNull() ?: '.'

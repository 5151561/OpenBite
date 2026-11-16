package com.openbite.core.model

/** 没有小数位的币种（ISO 4217 minor unit = 0）。 */
private val zeroDecimalCurrencies = setOf("JPY", "KRW", "VND", "TWD", "ISK", "CLP")

private val currencySymbols = mapOf(
    "JPY" to "¥",
    "CNY" to "¥",
    "USD" to "$",
    "EUR" to "€",
    "GBP" to "£",
    "KRW" to "₩",
    "HKD" to "HK$",
    "TWD" to "NT$",
)

/** “¥9,200”“$12.50”。不认识的币种写成“THB 120.00”。两端界面共用。 */
fun formatMoney(money: Money): String {
    val decimals = if (money.currency in zeroDecimalCurrencies) 0 else 2
    val negative = money.amountMinor < 0
    val abs = if (negative) -money.amountMinor else money.amountMinor
    var unit = 1L
    repeat(decimals) { unit *= 10 }
    val whole = (abs / unit).toString().reversed().chunked(3).joinToString(",").reversed()
    val fraction = if (decimals == 0) "" else "." + (abs % unit).toString().padStart(decimals, '0')
    val symbol = currencySymbols[money.currency]
    val number = whole + fraction
    val body = if (symbol != null) symbol + number else "${money.currency} $number"
    return if (negative) "-$body" else body
}

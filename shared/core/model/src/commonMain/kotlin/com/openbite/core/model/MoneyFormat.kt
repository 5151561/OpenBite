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

/** 这个币种有几位小数（JPY 0，USD 2）。 */
fun currencyDecimals(currency: String): Int = if (currency.uppercase() in zeroDecimalCurrencies) 0 else 2

/**
 * 用户输入的金额 -> [Money]：“4,246”“¥4246”“12.5”都行。
 * 小数位多于币种允许的、负数、空的都返回 null。
 */
fun parseMoneyInput(text: String, currency: String): Money? {
    val cleaned = text.filter { it.isDigit() || it == '.' }
    if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
    val decimals = currencyDecimals(currency)
    val whole = cleaned.substringBefore('.')
    val fraction = cleaned.substringAfter('.', missingDelimiterValue = "")
    if (fraction.length > decimals) return null
    if (whole.length > 12) return null
    var unit = 1L
    repeat(decimals) { unit *= 10 }
    val amount = (whole.ifEmpty { "0" }.toLong()) * unit + (fraction.padEnd(decimals, '0').ifEmpty { "0" }.toLong())
    return Money(amount, currency.uppercase())
}

/** 编辑框里显示的金额，不带货币符号：4246、12.50。 */
fun moneyInputText(money: Money): String {
    val decimals = currencyDecimals(money.currency)
    if (decimals == 0) return money.amountMinor.toString()
    var unit = 1L
    repeat(decimals) { unit *= 10 }
    return "${money.amountMinor / unit}.${(money.amountMinor % unit).toString().padStart(decimals, '0')}"
}

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

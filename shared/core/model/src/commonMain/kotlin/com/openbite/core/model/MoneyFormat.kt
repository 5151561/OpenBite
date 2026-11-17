package com.openbite.core.model

/** 不认识的币种按两位小数处理。 */
private const val UNKNOWN_FRACTION_DIGITS = 2

/** 整数部分最多几位，防止 Long 溢出。 */
private const val MAX_WHOLE_DIGITS = 12

/** 这个币种有几位小数：按 ISO 4217（JPY 0、USD 2、KWD 3），不认识的按 2。 */
fun currencyDecimals(currency: String): Int = currencyFractionDigits(currency) ?: UNKNOWN_FRACTION_DIGITS

/** 三个字母、平台认识的币种代码。 */
fun isKnownCurrency(code: String): Boolean = code.length == 3 && currencyFractionDigits(code) != null

/**
 * 用户输入的金额 -> [Money]：“4,246”“¥4246”“12.5”“12,50”“1.234,50”都行。
 * 小数点认“.”和“,”：两种都出现时后出现的是小数点；只出现一种、后面正好三位数、
 * 又是这个语言的分组符（或币种没有小数）时当分组符，否则当小数点。空格、撇号等分组符直接忽略。
 * 小数位多于币种允许的、负数、空的、分组位置不对的都返回 null。
 */
fun parseMoneyInput(text: String, currency: String, localeTag: String = currentLocaleTag()): Money? {
    if (text.any { it == '-' || it == '\u2212' }) return null
    val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }
    if (cleaned.none { it.isDigit() }) return null
    val decimals = currencyDecimals(currency)
    val decimalIndex = decimalSeparatorIndex(cleaned, decimals, localeTag) ?: return null
    val wholePart = if (decimalIndex >= 0) cleaned.substring(0, decimalIndex) else cleaned
    val fraction = if (decimalIndex >= 0) cleaned.substring(decimalIndex + 1) else ""
    if (fraction.any { !it.isDigit() }) return null
    val whole = groupedDigits(wholePart) ?: return null
    if (fraction.length > decimals || whole.length > MAX_WHOLE_DIGITS) return null
    var unit = 1L
    repeat(decimals) { unit *= 10 }
    val amount = whole.ifEmpty { "0" }.toLong() * unit + fraction.padEnd(decimals, '0').ifEmpty { "0" }.toLong()
    return Money(amount, currency.uppercase())
}

/** 小数点在 [cleaned] 里的位置；-1 表示没有小数点；null 表示认不出来。 */
private fun decimalSeparatorIndex(cleaned: String, decimals: Int, localeTag: String): Int? {
    val dots = cleaned.count { it == '.' }
    val commas = cleaned.count { it == ',' }
    if (dots == 0 && commas == 0) return -1
    val last = cleaned.indexOfLast { it == '.' || it == ',' }
    val lastChar = cleaned[last]
    if (dots > 0 && commas > 0) {
        // “1,234.50”“1.234,50”：后出现的只能有一个，它是小数点。
        return if (cleaned.count { it == lastChar } == 1) last else null
    }
    // 只有一种分隔符。
    if (cleaned.count { it == lastChar } > 1) return -1
    val digitsAfter = cleaned.length - last - 1
    val localeDecimal = decimalSeparator(localeTag)
    val isGrouping = digitsAfter == 3 && (decimals == 0 || lastChar != localeDecimal)
    return if (isGrouping) -1 else last
}

/** 去掉整数部分的分组符；分组位置不对（空组、最后一组不是三位）返回 null。 */
private fun groupedDigits(whole: String): String? {
    if (whole.all { it.isDigit() }) return whole
    val groups = whole.split('.', ',')
    if (groups.any { it.isEmpty() }) return null
    if (groups.drop(1).any { it.length !in 2..3 } || groups.last().length != 3) return null
    return groups.joinToString("")
}

/** 编辑框里显示的金额，不带符号、不分组，小数点按 [localeTag]：4246、12.50（de 下 12,50）。 */
fun moneyInputText(money: Money, localeTag: String = currentLocaleTag()): String {
    val decimals = currencyDecimals(money.currency)
    if (decimals == 0) return money.amountMinor.toString()
    var unit = 1L
    repeat(decimals) { unit *= 10 }
    val fraction = (money.amountMinor % unit).toString().padStart(decimals, '0')
    return "${money.amountMinor / unit}${decimalSeparator(localeTag)}$fraction"
}

/**
 * 按手机语言写金额：zh-CN 下“€12.50”“JP¥4,246”，de-DE 下“12,50 €”。
 * 小数位按 ISO 4217。不认识的币种写成“XXX 12.00”。两端界面共用。
 */
fun formatMoney(money: Money, localeTag: String = currentLocaleTag()): String {
    val code = money.currency.uppercase()
    val digits = currencyFractionDigits(code)
        ?: return "$code ${formatPlainNumber(money.amountMinor, UNKNOWN_FRACTION_DIGITS, localeTag)}"
    return formatCurrencyAmount(money.amountMinor, code, digits, localeTag)
}

/** 币种选择器里一行的名字：“EUR · 欧元”。 */
fun currencyLabel(code: String, localeTag: String = currentLocaleTag()): String {
    val name = currencyDisplayName(code, localeTag)
    return if (name.equals(code, ignoreCase = true)) code.uppercase() else "${code.uppercase()} · $name"
}

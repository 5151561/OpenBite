package com.openbite.core.model

// 货币和地区的平台数据：ISO 4217 小数位、本地化名称和符号、国家 -> 币种、按手机语言格式化。
// 不手写币种表：Android 用 java.util.Currency / NumberFormat，iOS 用 NSLocale / NSNumberFormatter。

/** 手机当前的界面语言（BCP 47），如 "zh-CN"。 */
expect fun currentLocaleTag(): String

/** 手机系统设置的地区（ISO 3166 两位大写字母），不跟着 App 单独设的语言走；读不到为 null。 */
expect fun currentRegion(): String?

/** ISO 4217 币种的小数位（JPY 0、EUR 2、KWD 3）；不认识的代码或没有小数位定义的（XXX、XAU）返回 null。 */
expect fun currencyFractionDigits(code: String): Int?

/** 币种在 [localeTag] 语言里的名字：EUR -> “欧元”。不认识时返回代码本身。 */
expect fun currencyDisplayName(code: String, localeTag: String = currentLocaleTag()): String

/** 币种在 [localeTag] 语言里的符号：zh-CN 下 EUR -> “€”、JPY -> “JP¥”。不认识时返回代码本身。 */
expect fun currencySymbol(code: String, localeTag: String = currentLocaleTag()): String

/** 现在各国家/地区在用的币种代码（去重，按代码排序）。 */
expect fun availableCurrencyCodes(): List<String>

/** 国家/地区（ISO 3166 两位字母）用的币种：JP -> JPY。不认识或没有法定货币（南极洲）返回 null。 */
expect fun currencyForCountry(countryCode: String): String?

/** 语言 [languageTag] 在 [inLocaleTag] 里的名字：de 在 zh-CN 下 -> “德语”，在 de 下 -> “Deutsch”。 */
expect fun languageDisplayName(languageTag: String, inLocaleTag: String = currentLocaleTag()): String

/** 按 [localeTag] 的格式写金额（带符号）。[code] 必须是认识的币种，[fractionDigits] 是它的小数位。 */
internal expect fun formatCurrencyAmount(amountMinor: Long, code: String, fractionDigits: Int, localeTag: String): String

/** 按 [localeTag] 的格式写数字（有分组，固定 [fractionDigits] 位小数），不带符号。 */
internal expect fun formatPlainNumber(amountMinor: Long, fractionDigits: Int, localeTag: String): String

/** [localeTag] 的小数点（en “.”，de “,”）。 */
internal expect fun decimalSeparator(localeTag: String): Char

/** 一次默认币种是从哪里来的。界面显示“来自：…”。 */
enum class CurrencySource {
    /** 用户在这道菜 / 这张小票上自己选的（或已经存过的）。 */
    MANUAL,

    /** 店家小票上读出来的。 */
    RECEIPT,

    /** 菜单上写的（识别抬头或菜品价格）。 */
    MENU,

    /** 这家店已有的菜用的币种。 */
    PLACE,

    /** 店铺位置所在的国家/地区。 */
    PLACE_COUNTRY,

    /** 设置里的“备用币种”。 */
    SETTINGS,

    /** 手机地区。 */
    PHONE_REGION,
}

/** 选好的默认币种和它的来源。 */
data class CurrencyChoice(val code: String, val source: CurrencySource)

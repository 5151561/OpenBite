package com.openbite.core.domain

import kotlinx.serialization.Serializable

/**
 * 一种语言的小票句子表。内置表手写、离线可用；别的语言由 AI 照英文表翻成同样的结构（JSON 键一样）。
 * - [allergens] 按 [com.openbite.core.model.Allergen] 的顺序 10 个，[avoids] 按 AvoidItem 6 个，[spice] 按 SpiceLevel 4 句；
 * - 两个模板里的 `{items}` 换成连好的名字；
 * - 连名字：两项用 [andWord] / [orWord]，三项以上前面用 [listSeparator]，[serialComma] 为 true 时最后一项前也加逗号（英语 “a, b, and c”）。
 */
@Serializable
data class PhraseTable(
    val header: String,
    val allergens: List<String>,
    val avoids: List<String>,
    val spice: List<String>,
    val allergyTemplate: String,
    val avoidTemplate: String,
    val disclaimer: String,
    val listSeparator: String,
    val andWord: String,
    val orWord: String,
    val serialComma: Boolean = false,
) {
    fun allergySentence(names: List<String>): String? =
        names.takeIf { it.isNotEmpty() }?.let { allergyTemplate.replace(ITEMS, joinList(it, andWord)) }

    fun avoidSentence(names: List<String>): String? =
        names.takeIf { it.isNotEmpty() }?.let { avoidTemplate.replace(ITEMS, joinList(it, orWord)) }

    /** 按这种语言的习惯把名字连起来。 */
    fun joinList(items: List<String>, conjunction: String): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> items[0] + conjunction + items[1]
        else -> {
            val head = items.dropLast(1).joinToString(listSeparator)
            val comma = if (serialComma) listSeparator.trimEnd() else ""
            head + comma + conjunction + items.last()
        }
    }

    companion object {
        const val ITEMS = "{items}"
        const val ALLERGEN_COUNT = 10
        const val AVOID_COUNT = 6
        const val SPICE_COUNT = 4
    }
}

/** 内置的小票句子表，键是 [phraseTag] 规范过的语言标签。 */
object PhraseTables {
    val ZH_HANS = PhraseTable(
        header = "我要点这些",
        allergens = listOf("花生", "虾蟹", "蛋", "乳制品", "小麦", "大豆", "鱼", "坚果", "芝麻", "荞麦"),
        avoids = listOf("葱", "香菜", "内脏", "生食", "牛肉", "猪肉"),
        spice = listOf("不辣", "微辣", "中辣", "特辣"),
        allergyTemplate = "{items}过敏",
        avoidTemplate = "不要{items}",
        disclaimer = "过敏原为 AI 推测，仅供参考，请向店员确认。",
        listSeparator = "、",
        andWord = "、",
        orWord = "、",
    )

    val ZH_HANT = PhraseTable(
        header = "我要點這些",
        allergens = listOf("花生", "蝦蟹", "蛋", "乳製品", "小麥", "黃豆", "魚", "堅果", "芝麻", "蕎麥"),
        avoids = listOf("蔥", "香菜", "內臟", "生食", "牛肉", "豬肉"),
        spice = listOf("不要辣", "小辣", "中辣", "大辣"),
        allergyTemplate = "{items}過敏",
        avoidTemplate = "不要{items}",
        disclaimer = "過敏原為 AI 推測，僅供參考，請向店員確認。",
        listSeparator = "、",
        andWord = "、",
        orWord = "、",
    )

    val JA = PhraseTable(
        header = "注文をお願いします",
        allergens = listOf("ピーナッツ", "えび・かに", "卵", "乳製品", "小麦", "大豆", "魚", "ナッツ", "ごま", "そば"),
        avoids = listOf("ネギ", "パクチー", "ホルモン", "生もの", "牛肉", "豚肉"),
        spice = listOf("辛くしないでください", "辛さ控えめでお願いします", "普通の辛さでお願いします", "辛口でお願いします"),
        allergyTemplate = "{items}アレルギーがあります",
        avoidTemplate = "{items}抜きでお願いします",
        disclaimer = "アレルギー表示はAIの推定です。必ず店員さんにご確認ください。",
        listSeparator = "・",
        andWord = "・",
        orWord = "・",
    )

    val KO = PhraseTable(
        header = "이렇게 주문할게요",
        allergens = listOf("땅콩", "갑각류(새우·게)", "달걀", "유제품", "밀", "대두", "생선", "견과류", "참깨", "메밀"),
        avoids = listOf("파", "고수", "내장", "날것", "소고기", "돼지고기"),
        spice = listOf("안 맵게 해 주세요.", "조금만 맵게 해 주세요.", "보통 맵기로 해 주세요.", "아주 맵게 해 주세요."),
        allergyTemplate = "{items} 알레르기가 있어요.",
        avoidTemplate = "{items} 빼 주세요.",
        disclaimer = "알레르기 정보는 AI가 추정한 것입니다. 꼭 직원에게 확인해 주세요.",
        listSeparator = ", ",
        andWord = ", ",
        orWord = ", ",
    )

    val EN = PhraseTable(
        header = "I'd like to order:",
        allergens = listOf("peanuts", "shellfish", "eggs", "dairy", "wheat", "soy", "fish", "tree nuts", "sesame", "buckwheat"),
        avoids = listOf("green onion", "cilantro", "offal", "raw food", "beef", "pork"),
        spice = listOf("Not spicy, please.", "Only a little spicy, please.", "Medium spicy, please.", "Extra spicy, please."),
        allergyTemplate = "I'm allergic to {items}.",
        avoidTemplate = "No {items}, please.",
        disclaimer = "Allergen notes are an AI guess. Please check with the staff.",
        listSeparator = ", ",
        andWord = " and ",
        orWord = " or ",
        serialComma = true,
    )

    val FR = PhraseTable(
        header = "Je voudrais commander :",
        allergens = listOf("arachides", "crustacés", "œufs", "produits laitiers", "blé", "soja", "poisson", "fruits à coque", "sésame", "sarrasin"),
        avoids = listOf("oignon vert", "coriandre", "abats", "aliments crus", "bœuf", "porc"),
        spice = listOf(
            "Pas épicé, s'il vous plaît.",
            "Légèrement épicé, s'il vous plaît.",
            "Moyennement épicé, s'il vous plaît.",
            "Très épicé, s'il vous plaît.",
        ),
        allergyTemplate = "J'ai une allergie aux aliments suivants : {items}.",
        avoidTemplate = "Sans {items}, s'il vous plaît.",
        disclaimer = "Les allergènes sont une estimation de l'IA. Merci de vérifier auprès du personnel.",
        listSeparator = ", ",
        andWord = " et ",
        orWord = " ni ",
    )

    val DE = PhraseTable(
        header = "Ich möchte bitte bestellen:",
        allergens = listOf("Erdnüsse", "Krebstiere", "Eier", "Milchprodukte", "Weizen", "Soja", "Fisch", "Schalenfrüchte", "Sesam", "Buchweizen"),
        avoids = listOf("Frühlingszwiebeln", "Koriander", "Innereien", "rohe Speisen", "Rindfleisch", "Schweinefleisch"),
        spice = listOf("Bitte nicht scharf.", "Bitte nur leicht scharf.", "Bitte mittelscharf.", "Bitte sehr scharf."),
        allergyTemplate = "Ich bin allergisch gegen {items}.",
        avoidTemplate = "Bitte ohne {items}.",
        disclaimer = "Die Allergenangaben sind eine KI-Schätzung. Bitte fragen Sie beim Personal nach.",
        listSeparator = ", ",
        andWord = " und ",
        orWord = " und ",
    )

    val ES = PhraseTable(
        header = "Quisiera pedir:",
        allergens = listOf("cacahuetes", "mariscos", "huevo", "lácteos", "trigo", "soja", "pescado", "frutos secos", "sésamo", "trigo sarraceno"),
        avoids = listOf("cebolleta", "cilantro", "vísceras", "comida cruda", "ternera", "cerdo"),
        spice = listOf("Sin picante, por favor.", "Poco picante, por favor.", "Picante medio, por favor.", "Muy picante, por favor."),
        allergyTemplate = "Tengo alergia a: {items}.",
        avoidTemplate = "Sin {items}, por favor.",
        disclaimer = "Los alérgenos son una estimación de la IA. Por favor, confírmelo con el personal.",
        listSeparator = ", ",
        andWord = " y ",
        orWord = " ni ",
    )

    val IT = PhraseTable(
        header = "Vorrei ordinare:",
        allergens = listOf("arachidi", "crostacei", "uova", "latticini", "grano", "soia", "pesce", "frutta a guscio", "sesamo", "grano saraceno"),
        avoids = listOf("cipollotto", "coriandolo", "frattaglie", "cibi crudi", "manzo", "maiale"),
        spice = listOf("Non piccante, per favore.", "Poco piccante, per favore.", "Piccante medio, per favore.", "Molto piccante, per favore."),
        allergyTemplate = "Ho un'allergia a: {items}.",
        avoidTemplate = "Senza {items}, per favore.",
        disclaimer = "Gli allergeni sono una stima dell'IA. Per favore, verificate con il personale.",
        listSeparator = ", ",
        andWord = " e ",
        orWord = " e ",
    )

    val PT = PhraseTable(
        header = "Gostaria de pedir:",
        allergens = listOf("amendoim", "crustáceos", "ovos", "laticínios", "trigo", "soja", "peixe", "castanhas", "gergelim", "trigo-sarraceno"),
        avoids = listOf("cebolinha", "coentro", "miúdos", "comida crua", "carne bovina", "carne de porco"),
        spice = listOf("Sem pimenta, por favor.", "Pouco apimentado, por favor.", "Apimentado médio, por favor.", "Bem apimentado, por favor."),
        allergyTemplate = "Tenho alergia a: {items}.",
        avoidTemplate = "Sem {items}, por favor.",
        disclaimer = "Os alérgenos são uma estimativa da IA. Por favor, confirme com a equipe.",
        listSeparator = ", ",
        andWord = " e ",
        orWord = " e ",
    )

    val TH = PhraseTable(
        header = "ขอสั่งตามนี้",
        allergens = listOf("ถั่วลิสง", "สัตว์มีเปลือก (กุ้ง ปู)", "ไข่", "ผลิตภัณฑ์นม", "แป้งสาลี", "ถั่วเหลือง", "ปลา", "ถั่วเปลือกแข็ง", "งา", "บักวีต"),
        avoids = listOf("ต้นหอม", "ผักชี", "เครื่องใน", "อาหารดิบ", "เนื้อวัว", "เนื้อหมู"),
        spice = listOf("ขอไม่เผ็ด", "ขอเผ็ดน้อย", "ขอเผ็ดกลาง", "ขอเผ็ดมาก"),
        allergyTemplate = "แพ้{items}",
        avoidTemplate = "ไม่ใส่{items}",
        disclaimer = "ข้อมูลสารก่อภูมิแพ้เป็นการคาดเดาของ AI กรุณาสอบถามพนักงานอีกครั้ง",
        listSeparator = " ",
        andWord = " และ",
        orWord = " และ",
    )

    val VI = PhraseTable(
        header = "Cho tôi gọi các món này:",
        allergens = listOf("đậu phộng", "tôm cua", "trứng", "sản phẩm từ sữa", "lúa mì", "đậu nành", "cá", "các loại hạt", "mè", "kiều mạch"),
        avoids = listOf("hành lá", "rau mùi", "nội tạng", "đồ sống", "thịt bò", "thịt heo"),
        spice = listOf("Xin đừng làm cay.", "Xin làm hơi cay thôi.", "Xin làm cay vừa.", "Xin làm thật cay."),
        allergyTemplate = "Tôi bị dị ứng với {items}.",
        avoidTemplate = "Xin đừng cho {items}.",
        disclaimer = "Thông tin dị ứng do AI dự đoán. Vui lòng hỏi lại nhân viên.",
        listSeparator = ", ",
        andWord = " và ",
        orWord = " và ",
    )

    val ID = PhraseTable(
        header = "Saya mau pesan:",
        allergens = listOf("kacang tanah", "udang/kepiting", "telur", "produk susu", "gandum", "kedelai", "ikan", "kacang-kacangan", "wijen", "soba"),
        avoids = listOf("daun bawang", "ketumbar", "jeroan", "makanan mentah", "daging sapi", "daging babi"),
        spice = listOf("Tolong tidak pedas.", "Tolong sedikit pedas saja.", "Tolong pedas sedang.", "Tolong pedas sekali."),
        allergyTemplate = "Saya alergi {items}.",
        avoidTemplate = "Tolong tanpa {items}.",
        disclaimer = "Info alergen ini perkiraan AI. Mohon dipastikan ke staf.",
        listSeparator = ", ",
        andWord = " dan ",
        orWord = " dan ",
        serialComma = true,
    )

    val builtIn: Map<String, PhraseTable> = mapOf(
        "zh-Hans" to ZH_HANS,
        "zh-Hant" to ZH_HANT,
        "ja" to JA,
        "ko" to KO,
        "en" to EN,
        "fr" to FR,
        "de" to DE,
        "es" to ES,
        "it" to IT,
        "pt" to PT,
        "th" to TH,
        "vi" to VI,
        "id" to ID,
    )

    fun builtIn(tag: String?): PhraseTable? = tag?.let { builtIn[it] }
}

/** 老代码里的语言别名（Java Locale 的旧代码）。 */
private val legacyLanguages = mapOf("in" to "id", "iw" to "he", "ji" to "yi", "tl" to "fil")

/** 繁体中文地区。 */
private val hantRegions = setOf("tw", "hk", "mo")

/**
 * BCP 47 -> 句子表的键：只留语言（pt-BR -> pt、en-US -> en），中文按文字分简繁
 * （zh-TW、zh-HK、zh-Hant -> zh-Hant，其余 zh -> zh-Hans）。空的、认不出的返回 null。
 */
fun phraseTag(tag: String?): String? {
    val parts = tag?.trim()?.replace('_', '-')?.split('-')?.filter { it.isNotEmpty() } ?: return null
    val language = parts.firstOrNull()?.lowercase()?.takeIf { it.length in 2..3 && it.all(Char::isLetter) } ?: return null
    if (language == "und") return null
    val normalized = legacyLanguages[language] ?: language
    if (normalized == "zh") {
        val rest = parts.drop(1).map { it.lowercase() }
        return when {
            "hant" in rest -> "zh-Hant"
            "hans" in rest -> "zh-Hans"
            rest.any { it in hantRegions } -> "zh-Hant"
            else -> "zh-Hans"
        }
    }
    return normalized
}

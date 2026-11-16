package com.openbite.core.model

import kotlinx.serialization.Serializable

/** 过敏原。[code] 与识别 Prompt 里约定的过敏原代码一致。顺序就是界面上的顺序。 */
@Serializable
enum class Allergen(val code: String) {
    PEANUT("peanut"),
    SHELLFISH("shellfish"),
    EGG("egg"),
    MILK("milk"),
    WHEAT("wheat"),
    SOY("soy"),
    FISH("fish"),
    TREE_NUT("tree_nut"),
    SESAME("sesame"),
    BUCKWHEAT("buckwheat"),
}

/** 不吃、不要的东西。 */
@Serializable
enum class AvoidItem { GREEN_ONION, CILANTRO, OFFAL, RAW, BEEF, PORK }

@Serializable
enum class SpiceLevel { NONE, MILD, MEDIUM, HOT }

/** 我的口味。整份存成一个 JSON 值。 */
@Serializable
data class TasteProfile(
    val allergens: List<Allergen> = emptyList(),
    val avoids: List<AvoidItem> = emptyList(),
    /** null：没要求，小票上不写辣度。 */
    val spice: SpiceLevel? = null,
    /** 每次点单自动带上。 */
    val autoApply: Boolean = true,
) {
    val isEmpty: Boolean get() = allergens.isEmpty() && avoids.isEmpty() && spice == null

    fun toggle(allergen: Allergen) = copy(
        allergens = if (allergen in allergens) allergens - allergen else (allergens + allergen).sortedBy { it.ordinal },
    )

    fun toggle(avoid: AvoidItem) = copy(
        avoids = if (avoid in avoids) avoids - avoid else (avoids + avoid).sortedBy { it.ordinal },
    )

    companion object {
        val EMPTY = TasteProfile()
    }
}

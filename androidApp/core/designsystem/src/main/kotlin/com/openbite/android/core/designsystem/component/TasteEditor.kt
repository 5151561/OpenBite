package com.openbite.android.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.openbite.android.core.designsystem.R
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.Allergen
import com.openbite.core.model.AvoidItem
import com.openbite.core.model.SpiceLevel
import com.openbite.core.model.TasteProfile

@Composable
@ReadOnlyComposable
fun allergenLabel(allergen: Allergen): String = stringArrayResource(R.array.ds_allergen_names)[allergen.ordinal]

@Composable
@ReadOnlyComposable
fun avoidLabel(avoid: AvoidItem): String = stringArrayResource(R.array.ds_avoid_names)[avoid.ordinal]

@Composable
@ReadOnlyComposable
fun spiceLabel(level: SpiceLevel): String = stringArrayResource(R.array.ds_spice_names)[level.ordinal]

/** “可能含 虾蟹、小麦”。 */
@Composable
@ReadOnlyComposable
fun mayContainLabel(allergens: List<Allergen>): String =
    stringResource(R.string.ds_may_contain, allergens.map { allergenLabel(it) }.joinToString(stringResource(R.string.ds_list_separator)))

/** 过敏、不吃、辣度三组选择。“我的口味”页和点单页的“这次改一下”共用。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TasteEditor(
    profile: TasteProfile,
    onToggleAllergen: (Allergen) -> Unit,
    onToggleAvoid: (AvoidItem) -> Unit,
    onSpice: (SpiceLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader(stringResource(R.string.ds_taste_allergens), color = MaterialTheme.colorScheme.primary)
        ChipGroup {
            Allergen.entries.forEach { allergen ->
                FilterChip(
                    selected = allergen in profile.allergens,
                    onClick = { onToggleAllergen(allergen) },
                    label = { Text(allergenLabel(allergen)) },
                )
            }
        }
        SectionHeader(stringResource(R.string.ds_taste_avoids), color = MaterialTheme.colorScheme.primary)
        ChipGroup {
            AvoidItem.entries.forEach { avoid ->
                FilterChip(
                    selected = avoid in profile.avoids,
                    onClick = { onToggleAvoid(avoid) },
                    label = { Text(avoidLabel(avoid)) },
                )
            }
        }
        SectionHeader(stringResource(R.string.ds_taste_spice), color = MaterialTheme.colorScheme.primary)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            SpiceLevel.entries.forEach { level ->
                SegmentedButton(
                    selected = profile.spice == level,
                    onClick = { onSpice(level) },
                    shape = SegmentedButtonDefaults.itemShape(level.ordinal, SpiceLevel.entries.size),
                    label = { Text(spiceLabel(level)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) { content() }
}

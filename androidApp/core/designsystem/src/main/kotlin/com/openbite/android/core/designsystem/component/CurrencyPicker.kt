package com.openbite.android.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import com.openbite.android.core.designsystem.R
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.core.designsystem.theme.Spacing
import com.openbite.core.model.CurrencySource
import com.openbite.core.model.availableCurrencyCodes
import com.openbite.core.model.currencyDisplayName
import com.openbite.core.model.currencyLabel
import com.openbite.core.model.currencySymbol

/** “来自：菜单”这类小字；用户自己选的不显示。 */
@Composable
fun currencySourceLabel(source: CurrencySource?): String? = when (source) {
    null, CurrencySource.MANUAL -> null
    CurrencySource.RECEIPT -> stringResource(R.string.ds_currency_from_receipt)
    CurrencySource.MENU -> stringResource(R.string.ds_currency_from_menu)
    CurrencySource.PLACE -> stringResource(R.string.ds_currency_from_place)
    CurrencySource.PLACE_COUNTRY -> stringResource(R.string.ds_currency_from_place_country)
    CurrencySource.SETTINGS -> stringResource(R.string.ds_currency_from_settings)
    CurrencySource.PHONE_REGION -> stringResource(R.string.ds_currency_from_phone_region)
}

/** 当前界面语言的 BCP 47 标签，币种名称和符号按它显示。 */
@Composable
private fun uiLocaleTag(): String = ComposeLocale.current.toLanguageTag()

/** 币种输入框：显示“EUR · 欧元”，下面一行小字“来自：…”。点开选择器。 */
@Composable
fun CurrencyField(
    code: String,
    source: CurrencySource?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val tag = uiLocaleTag()
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, scheme.outline),
        color = scheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(R.string.ds_currency), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(currencyLabel(code, tag), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                currencySourceLabel(source)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Icon(OpenBiteIcons.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * 选币种：搜代码或本地化名称；上面“最近用过”，下面全部币种按名称排序。每行“EUR · 欧元  €”。
 * [followRegion] 不为 null 时最上面多一项“跟随地区 · CNY”（设置页的备用币种用），选它回调 null。
 */
@Composable
fun CurrencyPickerDialog(
    selected: String?,
    recent: List<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    followRegion: String? = null,
) {
    val tag = uiLocaleTag()
    val all = remember(tag) {
        availableCurrencyCodes().map { CurrencyRow(it, currencyDisplayName(it, tag), currencySymbol(it, tag)) }.sortedBy { it.name }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val matches = if (q.isEmpty()) all else all.filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
    val recentRows = if (q.isEmpty()) recent.mapNotNull { code -> all.firstOrNull { it.code == code } } else emptyList()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxSize(0.85f).imePadding(),
        ) {
            Column(Modifier.padding(vertical = Spacing.l)) {
                Row(Modifier.padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ds_currency_picker_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ds_currency_cancel)) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.ds_currency_search)) },
                    leadingIcon = { Icon(OpenBiteIcons.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(OpenBiteIcons.Close, contentDescription = stringResource(R.string.ds_currency_clear_search))
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    if (followRegion != null && q.isEmpty()) {
                        item(key = "follow-region") {
                            PickerRow(stringResource(R.string.ds_currency_follow_region, followRegion), null, selected == null) { onPick(null) }
                        }
                    }
                    if (recentRows.isNotEmpty()) {
                        item(key = "recent-header") { SectionHeader(stringResource(R.string.ds_currency_recent)) }
                        items(recentRows, key = { "recent-${it.code}" }) { row ->
                            PickerRow(row.label, row.symbol, row.code == selected) { onPick(row.code) }
                        }
                        item(key = "all-header") { SectionHeader(stringResource(R.string.ds_currency_all)) }
                    }
                    items(matches, key = { it.code }) { row ->
                        PickerRow(row.label, row.symbol, row.code == selected) { onPick(row.code) }
                    }
                    if (matches.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.ds_currency_not_found, q),
                                Modifier.padding(Spacing.xxl),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CurrencyRow(val code: String, val name: String, val symbol: String) {
    val label: String get() = if (name.equals(code, ignoreCase = true)) code else "$code · $name"
}

@Composable
private fun PickerRow(label: String, symbol: String?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.xxl, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // 符号和代码一样时（没有专门符号）不重复显示。
        if (symbol != null && !label.startsWith(symbol)) {
            Text(symbol, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
        if (selected) Icon(OpenBiteIcons.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun CurrencyFieldPreview() {
    OpenBiteTheme(darkTheme = false) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CurrencyField("EUR", CurrencySource.PLACE_COUNTRY, onClick = {})
            CurrencyField("JPY", CurrencySource.MANUAL, onClick = {})
        }
    }
}

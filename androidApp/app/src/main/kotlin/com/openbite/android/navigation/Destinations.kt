package com.openbite.android.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.openbite.android.R
import com.openbite.android.core.designsystem.icon.OpenBiteIcons
import kotlinx.serialization.Serializable

// 路由键。feature 模块不认识它们，跳转都在 :androidApp:app 里接。

@Serializable
data object HomeKey : NavKey

@Serializable
data object MemoriesKey : NavKey

@Serializable
data object PlacesKey : NavKey

@Serializable
data object MilestonesKey : NavKey

@Serializable
data object SettingsKey : NavKey

/** 店铺详情，压在当前 Tab 的返回栈上。 */
@Serializable
data class PlaceKey(val placeId: String) : NavKey

/** 电子菜单与原照片；[showPhotos] 为 true 时直接打开“原照片” Tab。 */
@Serializable
data class PlaceMenuKey(val placeId: String, val showPhotos: Boolean = false) : NavKey

/** 底部导航的 4 个 Tab：今天 / 记忆 / 店铺 / 纪念日。 */
enum class TopLevelDestination(
    val key: NavKey,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    TODAY(HomeKey, R.string.tab_today, OpenBiteIcons.Today, OpenBiteIcons.Today),
    MEMORIES(MemoriesKey, R.string.tab_memories, OpenBiteIcons.BookmarkBorder, OpenBiteIcons.Bookmark),
    PLACES(PlacesKey, R.string.tab_places, OpenBiteIcons.LocationOnOutlined, OpenBiteIcons.LocationOn),
    MILESTONES(MilestonesKey, R.string.tab_milestones, OpenBiteIcons.FavoriteBorder, OpenBiteIcons.Favorite),
}

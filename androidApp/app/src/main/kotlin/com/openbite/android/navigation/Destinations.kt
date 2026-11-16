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

/** 识别菜单。[placeId] 不为 null 时结果挂到这家店；[fromGallery] 为 true 时从相册选，否则拍照。 */
@Serializable
data class ScanKey(val placeId: String? = null, val fromGallery: Boolean = false) : NavKey

/** 点单。[sessionId] 来自识别页（边识别边点单）；为 null 时用 [placeId] 店铺里存好的菜单。 */
@Serializable
data class OrderKey(val sessionId: String? = null, val placeId: String? = null) : NavKey

/** 给店员看的小票。 */
@Serializable
data class ReceiptKey(val sessionId: String) : NavKey

@Serializable
data object TasteKey : NavKey

/** 地图与时间胶囊（06）：压在当前 Tab 上，底部导航照常显示。[focusPlaceId] 是先选中的店。 */
@Serializable
data class MapKey(val focusPlaceId: String? = null) : NavKey

/** 给店铺在地图上选位置。 */
@Serializable
data class PickLocationKey(val placeId: String) : NavKey

/** 搜索：[places] 为 true 搜店铺，否则搜记忆。 */
@Serializable
data class SearchKey(val places: Boolean) : NavKey

/** 纪念日管理：[milestoneId] 为 null 是新建。 */
@Serializable
data class MilestoneEditKey(val milestoneId: String? = null) : NavKey

/** 补记忆（05）。 */
@Serializable
data class MemoryEditKey(val logId: String) : NavKey

/** 一餐（只读）。 */
@Serializable
data class MealKey(val logId: String) : NavKey

/** 小票存档（19）。 */
@Serializable
data class ReceiptArchiveKey(val logId: String) : NavKey

/** 分享这一餐（16）。 */
@Serializable
data class ShareMealKey(val logId: String) : NavKey

/** 分享店铺（17）。 */
@Serializable
data class SharePlaceKey(val placeId: String) : NavKey

/** 从通知或别的 App 进来要去的地方。 */
sealed interface DeepLink {
    /** 收到一个 .openbite 文件（content:// 地址）。 */
    data class ImportPlace(val uri: String) : DeepLink

    data class EditMeal(val logId: String) : DeepLink

    /** [logId]：去年那顿饭，有就打开。 */
    data class Milestones(val logId: String?) : DeepLink
}

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

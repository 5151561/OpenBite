package com.openbite.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.navigation.DeepLink
import com.openbite.android.navigation.OpenBiteApp
import com.openbite.android.reminders.Notifications

class MainActivity : ComponentActivity() {
    /** 从通知或别的 App 进来时要去的页面；导航处理完置空。 */
    private val deepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 转屏重建时 Intent 还在，但返回栈已经恢复了，不再跳一次。
        if (savedInstanceState == null) deepLink.value = intent.toDeepLink()
        setContent {
            OpenBiteTheme {
                OpenBiteApp(deepLink = deepLink.value, onDeepLinkHandled = { deepLink.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toDeepLink()?.let { deepLink.value = it }
    }

    private fun Intent.toDeepLink(): DeepLink? {
        when (action) {
            // 收到的店铺文件：只取 content:// 地址，内容到导入页再校验。
            Intent.ACTION_VIEW -> data?.takeIf { it.scheme == "content" }?.let { return DeepLink.ImportPlace(it.toString()) }
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
                ?.takeIf { it.scheme == "content" }
                ?.let { return DeepLink.ImportPlace(it.toString()) }
        }
        getStringExtra(Notifications.EXTRA_EDIT_LOG)?.let { return DeepLink.EditMeal(it) }
        if (getBooleanExtra(Notifications.EXTRA_MILESTONES, false)) return DeepLink.Milestones(getStringExtra(Notifications.EXTRA_VIEW_LOG))
        return null
    }
}

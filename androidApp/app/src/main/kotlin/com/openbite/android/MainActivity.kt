package com.openbite.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openbite.android.core.designsystem.OpenBiteTheme
import com.openbite.android.feature.journal.JournalRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OpenBiteTheme {
                // 只有一个页面时直接显示；加了扫描、地图等页面后在这里接导航。
                JournalRoute()
            }
        }
    }
}

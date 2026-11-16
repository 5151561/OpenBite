package com.openbite.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.openbite.android.core.designsystem.theme.OpenBiteTheme
import com.openbite.android.navigation.OpenBiteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OpenBiteTheme {
                OpenBiteApp()
            }
        }
    }
}

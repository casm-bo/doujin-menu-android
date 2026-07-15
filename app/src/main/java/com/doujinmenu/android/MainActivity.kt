package com.doujinmenu.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.doujinmenu.android.ui.DoujinMenuApp
import com.doujinmenu.android.ui.theme.DoujinMenuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoujinMenuTheme {
                DoujinMenuApp()
            }
        }
    }
}

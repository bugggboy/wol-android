package com.wolwake.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wolwake.app.ui.HomeScreen
import com.wolwake.app.ui.WolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WolTheme {
                HomeScreen()
            }
        }
    }
}

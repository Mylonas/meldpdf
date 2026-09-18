package com.mikmy.meldpdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import com.mikmy.meldpdf.ui.HomeScreen
import com.mikmy.meldpdf.ui.MeldTheme
import com.mikmy.meldpdf.ui.ToolScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MeldTheme {
                var toolId by rememberSaveable { mutableStateOf<String?>(null) }
                val tool = remember(toolId) { toolId?.let { Tools.byId(it) } }

                if (tool == null) {
                    HomeScreen(onOpen = { toolId = it.id })
                } else {
                    BackHandler { toolId = null }
                    ToolScreen(tool = tool, onBack = { toolId = null })
                }
            }
        }
    }
}

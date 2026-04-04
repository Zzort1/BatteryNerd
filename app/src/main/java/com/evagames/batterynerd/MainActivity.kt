package com.evagames.batterynerd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.evagames.batterynerd.ui.BatteryDashboard
import com.evagames.batterynerd.ui.theme.BatteryNerdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BatteryNerdTheme {
                BatteryDashboard()
            }
        }
    }
}

package au.com.evagames.batterynerd

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import au.com.evagames.batterynerd.ui.BatteryDashboard
import au.com.evagames.batterynerd.ui.theme.BatteryNerdTheme

class MainActivity : ComponentActivity() {

    private var isInPipMode by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        isInPipMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isInPictureInPictureMode
        } else {
            false
        }

        setContent {
            BatteryNerdTheme {
                BatteryDashboard(
                    isInPictureInPicture = isInPipMode,
                    onEnterPictureInPicture = ::enterBatteryPip
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    private fun enterBatteryPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val visibleRect = Rect()
        window.decorView.getGlobalVisibleRect(visibleRect)

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .setSourceRectHint(visibleRect)
            .build()

        enterPictureInPictureMode(params)
    }
}

package io.github.miuzarte.fhradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.miuzarte.fhradio.pages.MainScreen

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidBridge.init(this)
        enableEdgeToEdge()

        setContent {
            MainScreen()
        }

        onBackPressedDispatcher.addCallback(
            object: OnBackPressedCallback(true) {
                // 拦截返回手势, 避免返回到桌面时触发 onDestroy 导致播放器 dispose,
                // 直接返回桌面不影响
                override fun handleOnBackPressed() {}
            },
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            Scheduler.dispose()
            Radio.dispose()
            AppRuntime.dispose()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    MainScreen()
}

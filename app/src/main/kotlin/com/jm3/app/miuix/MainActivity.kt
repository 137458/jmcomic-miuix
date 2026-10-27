package com.jm3.app.miuix

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jm3.app.miuix.ui.ReaderVolumeKeys

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Jm3Graph.init(applicationContext)
        setContent {
            Jm3App()
        }
    }

    /** 阅读器挂载时把音量键交给翻页，其余场景保持系统音量行为。 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ReaderVolumeKeys.dispatch(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }
}

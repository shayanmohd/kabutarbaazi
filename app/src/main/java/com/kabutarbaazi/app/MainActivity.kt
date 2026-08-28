package com.kabutarbaazi.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.kabutarbaazi.app.ui.AppRoot
import com.kabutarbaazi.app.ui.theme.KabutarTheme

// AppCompatActivity rather than ComponentActivity: minSdk is 26, and AppCompatDelegate's
// per-app language backport only applies the selected locale to an AppCompat activity.
// Urdu and Hindi switching would silently do nothing on API 26-32 otherwise.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KabutarTheme {
                AppRoot()
            }
        }
    }
}

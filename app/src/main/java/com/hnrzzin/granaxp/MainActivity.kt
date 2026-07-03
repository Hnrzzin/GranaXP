package com.hnrzzin.granaxp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.hnrzzin.granaxp.navigation.AppNavigation
import com.hnrzzin.granaxp.ui.theme.GranaXPTheme // Verifique se o caminho do seu tema está correto
import com.hnrzzin.granaxp.ui.theme.GranaXPThemeColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)  // ✅ adicionar antes do setContent
        setContent {
            Log.d("TraceMain", "MainActivity ${hashCode()}")
            Log.d("TraceMain", "setContent")

            GranaXPThemeColors() {
                Log.d("TraceMain", "Theme")

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

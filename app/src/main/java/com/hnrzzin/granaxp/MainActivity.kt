package com.hnrzzin.granaxp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hnrzzin.granaxp.ui.navigation.AppNavigation
import com.hnrzzin.granaxp.ui.theme.GranaXPTheme // Verifique se o caminho do seu tema está correto

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ativa o modo de tela cheia (Edge-to-Edge) para um visual moderno
        enableEdgeToEdge()

        setContent {
            // Envolve o app no seu tema customizado
            GranaXPTheme {
                // Surface garante a cor de fundo correta do Material Theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Chama a navegação centralizada que criamos
                    AppNavigation()
                }
            }
        }
    }
}
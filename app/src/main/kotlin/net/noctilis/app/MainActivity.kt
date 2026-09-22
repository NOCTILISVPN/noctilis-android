package net.noctilis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Цвета NOCTILIS: ночь, холодный свет глаз персонажа.
private val Night = Color(0xFF0B1020)
private val Moon = Color(0xFF9FB8FF)
private val Fog = Color(0xFF8A93A8)

private val NoctilisColors = darkColorScheme(
    primary = Moon,
    background = Night,
    surface = Night,
    onBackground = Color.White,
    onSurface = Color.White,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = NoctilisColors) {
                Screen()
            }
        }
    }
}

@Composable
private fun Screen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "NOCTILIS",
            color = Moon,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Приложение собирается.",
            color = Color.White,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Сборка " + BuildConfig.VERSION_NAME,
            color = Fog,
            fontSize = 14.sp,
        )
    }
}

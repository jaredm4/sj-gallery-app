package app.sjgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.sjgallery.ui.navigation.SjGalleryNavHost
import app.sjgallery.ui.theme.SjGalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SjGalleryTheme {
                SjGalleryNavHost()
            }
        }
    }
}

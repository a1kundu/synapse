package `in`.arijitk.synapse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import `in`.arijitk.synapse.settings.ApplicationContextHolder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApplicationContextHolder.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

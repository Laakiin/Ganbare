package fr.lkn.ganbare

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint
import fr.lkn.ganbare.ui.AppNav
import fr.lkn.ganbare.ui.theme.GanbareTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            GanbareTheme { AppNav() }
        }
    }
}

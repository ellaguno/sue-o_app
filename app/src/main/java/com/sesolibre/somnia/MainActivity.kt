package com.sesolibre.somnia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sesolibre.somnia.service.MonitorService
import com.sesolibre.somnia.ui.home.HomeScreen
import com.sesolibre.somnia.ui.night.NightScreen
import com.sesolibre.somnia.ui.profile.ProfileScreen
import com.sesolibre.somnia.ui.questionnaires.EpworthScreen
import com.sesolibre.somnia.ui.questionnaires.StopBangScreen
import com.sesolibre.somnia.ui.settings.SettingsScreen
import com.sesolibre.somnia.ui.theme.SomniaTheme
import com.sesolibre.somnia.ui.trends.TrendsScreen
import dagger.hilt.android.AndroidEntryPoint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.sesolibre.somnia.data.prefs.SettingsRepository
import com.sesolibre.somnia.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    /** El micrófono se negó "para siempre": solo se puede conceder en Ajustes. */
    private var micBlocked by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[Manifest.permission.RECORD_AUDIO] == true) {
                MonitorService.start(this)
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                // Android ya no mostrará el diálogo: hay que mandar a Ajustes.
                micBlocked = true
            } else {
                Toast.makeText(this, R.string.mic_permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SomniaTheme {
                val onboardingDone by settings.onboardingDone.collectAsState(initial = null)
                val scope = rememberCoroutineScope()
                if (micBlocked) {
                    AlertDialog(
                        onDismissRequest = { micBlocked = false },
                        title = { Text(stringResource(R.string.mic_blocked_title)) },
                        text = { Text(stringResource(R.string.mic_blocked_body)) },
                        confirmButton = {
                            TextButton(onClick = {
                                micBlocked = false
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", packageName, null),
                                        ),
                                    )
                                }
                            }) { Text(stringResource(R.string.mic_blocked_action)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { micBlocked = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                    )
                }
                // Hasta leer la preferencia no se decide la pantalla inicial (evita un parpadeo).
                val done = onboardingDone ?: return@SomniaTheme
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = if (done) "home" else "welcome") {
                    composable("welcome") {
                        WelcomeScreen(onDone = {
                            scope.launch { settings.setOnboardingDone() }
                            nav.navigate("home") { popUpTo("welcome") { inclusive = true } }
                        })
                    }
                    composable("home") {
                        HomeScreen(
                            onRequestStart = ::startWithPermissions,
                            onOpenSession = { id -> nav.navigate("night/$id") },
                            onOpenProfile = { nav.navigate("profile") },
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenTrends = { nav.navigate("trends") },
                        )
                    }
                    composable(
                        route = "night/{sessionId}",
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
                    ) {
                        NightScreen(onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                    composable("trends") {
                        TrendsScreen(onBack = { nav.popBackStack() })
                    }
                    composable("profile") {
                        ProfileScreen(
                            onBack = { nav.popBackStack() },
                            onOpenEpworth = { nav.navigate("epworth") },
                            onOpenStopBang = { nav.navigate("stopbang") },
                        )
                    }
                    composable("epworth") {
                        EpworthScreen(onBack = { nav.popBackStack() })
                    }
                    composable("stopbang") {
                        StopBangScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    private fun startWithPermissions() {
        val needed = buildList {
            if (!granted(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isEmpty()) {
            MonitorService.start(this)
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

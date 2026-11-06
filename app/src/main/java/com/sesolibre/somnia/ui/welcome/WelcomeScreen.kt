package com.sesolibre.somnia.ui.welcome

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sesolibre.somnia.R

/**
 * Bienvenida (solo la primera vez): qué hace la app, dónde poner el teléfono,
 * qué se graba y cómo evitar que el sistema la cierre de noche.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Image(
                painter = painterResource(R.drawable.somnia_header),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.welcome_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            WelcomeCard(R.string.welcome_placement_title, R.string.welcome_placement_body)
            WelcomeCard(R.string.welcome_privacy_title, R.string.welcome_privacy_body)
            WelcomeCard(R.string.welcome_battery_title, R.string.welcome_battery_body) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    }
                }) { Text(stringResource(R.string.battery_card_action)) }
            }
            WelcomeCard(R.string.welcome_medical_title, R.string.welcome_medical_body)
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.welcome_start)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WelcomeCard(
    titleRes: Int,
    bodyRes: Int,
    action: (@Composable () -> Unit)? = null,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
}

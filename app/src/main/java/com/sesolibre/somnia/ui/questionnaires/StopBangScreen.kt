package com.sesolibre.somnia.ui.questionnaires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.questionnaires.StopBang

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopBangScreen(
    onBack: () -> Unit,
    viewModel: QuestionnaireViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val questions = stringArrayResource(R.array.stopbang_questions)
    val answers = remember { mutableStateListOf(*Array(StopBang.QUESTION_COUNT) { false }) }

    // Prellena B/A/N/G desde el perfil una sola vez (siguen siendo editables).
    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        StopBang.bmiItem(p.bmi)?.let { answers[4] = it }
        StopBang.ageItem(p.age)?.let { answers[5] = it }
        StopBang.neckItem(p.neckCm)?.let { answers[6] = it }
        StopBang.genderItem(p.sex)?.let { answers[7] = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stopbang_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.stopbang_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            questions.forEachIndexed { index, question ->
                Card {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            question,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = answers[index],
                            onCheckedChange = { answers[index] = it },
                        )
                    }
                }
            }
            Button(
                onClick = { viewModel.saveStopBang(answers.toList(), onSaved = onBack) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.questionnaire_submit)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

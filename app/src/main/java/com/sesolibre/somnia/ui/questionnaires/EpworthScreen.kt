package com.sesolibre.somnia.ui.questionnaires

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sesolibre.somnia.R
import com.sesolibre.somnia.questionnaires.Epworth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpworthScreen(
    onBack: () -> Unit,
    viewModel: QuestionnaireViewModel = hiltViewModel(),
) {
    val questions = stringArrayResource(R.array.epworth_questions)
    val options = stringArrayResource(R.array.epworth_options)
    val answers = remember { mutableStateListOf<Int?>(*arrayOfNulls(Epworth.QUESTION_COUNT)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.epworth_name)) },
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
                stringResource(R.string.epworth_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            questions.forEachIndexed { index, question ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${index + 1}. $question",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        options.forEachIndexed { value, option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = answers[index] == value,
                                        onClick = { answers[index] = value },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = answers[index] == value,
                                    onClick = { answers[index] = value },
                                )
                                Text(option, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    viewModel.saveEpworth(answers.map { it ?: 0 }, onSaved = onBack)
                },
                enabled = answers.none { it == null },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.questionnaire_submit)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

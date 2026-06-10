package com.hayat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hayat.R

@Composable
fun SettingsScreen(
    modelPath: String,
    dbPath: String,
    onPathsChanged: (String, String) -> Unit,
    onLanguageChanged: (String) -> Unit
) {
    var modelPathState by remember { mutableStateOf(modelPath) }
    var dbPathState by remember { mutableStateOf(dbPath) }
    var selectedLanguage by remember { mutableStateOf("en") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.title_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        TextField(
            value = modelPathState,
            onValueChange = { modelPathState = it },
            label = { Text(stringResource(R.string.label_model_path)) },
            modifier = Modifier.fillMaxWidth()
        )

        TextField(
            value = dbPathState,
            onValueChange = { dbPathState = it },
            label = { Text(stringResource(R.string.label_db_path)) },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onPathsChanged(modelPathState, dbPathState) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.button_save_settings))
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.label_language),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                onClick = {
                    selectedLanguage = "en"
                    onLanguageChanged("en")
                },
                label = { Text(stringResource(R.string.label_language_english)) },
                selected = selectedLanguage == "en",
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                onClick = {
                    selectedLanguage = "ar"
                    onLanguageChanged("ar")
                },
                label = { Text(stringResource(R.string.label_language_arabic)) },
                selected = selectedLanguage == "ar",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

package com.hayat.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hayat.services.P2PSharingService

@Composable
fun SettingsScreen(
    modelPath: String,
    dbPath: String,
    onPathsChanged: (String, String) -> Unit
) {
    var modelPathState by remember { mutableStateOf(modelPath) }
    var dbPathState by remember { mutableStateOf(dbPath) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "الإعدادات",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        TextField(
            value = modelPathState,
            onValueChange = { modelPathState = it },
            label = { Text("مسار النموذج (.gguf)") },
            modifier = Modifier.fillMaxWidth()
        )

        TextField(
            value = dbPathState,
            onValueChange = { dbPathState = it },
            label = { Text("مسار قاعدة البيانات (.db)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onPathsChanged(modelPathState, dbPathState) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حفظ الإعدادات")
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "مشاركة التطبيق",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Button(
            onClick = { P2PSharingService.getInstance(context).shareApkSimple() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "مشاركة",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("مشاركة التطبيق عبر Bluetooth/WiFi Direct")
        }
    }
}
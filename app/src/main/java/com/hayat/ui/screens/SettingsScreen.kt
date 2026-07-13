package com.hayat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hayat.ui.theme.DarkGray
import com.hayat.ui.theme.Gray

@Composable
fun SettingsScreen(
    modelPath: String,
    dbPath: String,
    onPathsChanged: (String, String) -> Unit,
    onNavigateToSharing: () -> Unit = {},
    onNavigateToCheatSheets: () -> Unit = {}
) {
    var modelPathState by remember { mutableStateOf(modelPath) }
    var dbPathState by remember { mutableStateOf(dbPath) }
    
    val menuItems = listOf(
        MenuItem(
            icon = Icons.Default.Share,
            title = "Share Hayat AI",
            description = "Share app and medical assets via Bluetooth/WiFi",
            onClick = onNavigateToSharing
        ),
        MenuItem(
            icon = Icons.Default.Print,
            title = "Print Emergency Sheets",
            description = "Generate printable PDF cheat sheets",
            onClick = onNavigateToCheatSheets
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Model Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedTextField(
                        value = modelPathState,
                        onValueChange = { modelPathState = it },
                        label = { Text("Model Path (.gguf)", color = Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Gray
                        )
                    )
                    
                    OutlinedTextField(
                        value = dbPathState,
                        onValueChange = { dbPathState = it },
                        label = { Text("Database Path (.db)", color = Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Gray
                        )
                    )
                    
                    Button(
                        onClick = { onPathsChanged(modelPathState, dbPathState) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Settings")
                    }
                }
            }
        }
        
        item {
            Text(
                text = "Distribution",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        items(menuItems.size) { index ->
            val item = menuItems[index]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onClick),
                shape = MaterialTheme.shapes.medium,
                color = DarkGray
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1565C0).copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = "About Hayat AI",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFF1565C0)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = """
                            Hayat AI is an offline medical assistant designed for crisis zones.
                            
                            Version: 1.0.0
                            Models: Local GGUF + ONNX Embeddings
                            Database: ICRC/WHO Emergency Protocols
                            
                            ⚠️ This app provides guidance only. Always seek professional medical help when available.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray
                    )
                }
            }
        }
    }
}

data class MenuItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

package com.hayat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hayat.services.PeerSharingService
import com.hayat.ui.theme.DarkGray
import com.hayat.ui.theme.Gray
import kotlinx.coroutines.launch

@Composable
fun SharingScreen(
    viewModel: SharingViewModel = viewModel(factory = SharingViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isSharing by remember { mutableStateOf(false) }
    var shareMessage by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    
    val sharingOptions = listOf(
        SharingOption(
            icon = Icons.Default.Share,
            title = "Share App (APK)",
            description = "Share the Hayat AI application with others via Bluetooth or messaging apps",
            action = { viewModel.shareApk(context) }
        ),
        SharingOption(
            icon = Icons.Default.Database,
            title = "Share Medical Database",
            description = "Share only the medical protocols database (smaller file size)",
            action = { viewModel.shareDatabase(context) }
        ),
        SharingOption(
            icon = Icons.Default.Folder,
            title = "Share All Assets",
            description = "Share complete package including model, database, and embeddings",
            action = { viewModel.shareAllAssets(context) }
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Share Hayat AI",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Spread life-saving knowledge to your community. Share offline via Bluetooth, WiFi Direct, or any file sharing app.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sharingOptions) { option ->
                    SharingOptionCard(
                        option = option,
                        enabled = !isSharing,
                        onClick = {
                            scope.launch {
                                isSharing = true
                                shareMessage = ""
                                val result = option.action()
                                result.fold(
                                    onSuccess = { 
                                        showSuccess = true
                                        shareMessage = "Ready to share! Choose an app to send the file."
                                    },
                                    onFailure = { error ->
                                        shareMessage = "Error: ${error.message}"
                                    }
                                )
                                isSharing = false
                            }
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (shareMessage.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (showSuccess) 
                                    Color(0xFF4CAF50).copy(alpha = 0.2f) 
                                else 
                                    Color(0xFFF44336).copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = shareMessage,
                                modifier = Modifier.padding(16.dp),
                                color = if (showSuccess) 
                                    Color(0xFF2E7D32) 
                                else 
                                    Color(0xFFC62828),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkGray)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "How P2P Sharing Works",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = """
                                    1. Select what you want to share (App, Database, or All Assets)
                                    2. Choose a sharing method (Bluetooth, WiFi Direct, Messaging apps)
                                    3. The recipient receives the file and can install/open it
                                    4. No internet connection required!
                                    
                                    This helps spread Hayat AI in areas with limited connectivity.
                                """.trimIndent(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Gray
                            )
                        }
                    }
                }
            }
        }
        
        if (isSharing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Preparing files...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SharingOptionCard(
    option: SharingOption,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) DarkGray else DarkGray.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray
                )
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

data class SharingOption(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val action: suspend () -> Result<Any>
)

class SharingViewModel(private val context: android.content.Context) : androidx.lifecycle.ViewModel() {
    
    private val sharingService = PeerSharingService(context)
    
    suspend fun shareApk(context: android.content.Context): Result<android.net.Uri> {
        return sharingService.shareApk()
    }
    
    suspend fun shareDatabase(context: android.content.Context): Result<android.net.Uri> {
        return sharingService.shareDatabase()
    }
    
    suspend fun shareAllAssets(context: android.content.Context): Result<List<android.net.Uri>> {
        return sharingService.shareAllAssets()
    }
}

class SharingViewModelFactory(private val context: android.content.Context) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return SharingViewModel(context) as T
    }
}

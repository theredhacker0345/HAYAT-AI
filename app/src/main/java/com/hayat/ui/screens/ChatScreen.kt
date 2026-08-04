package com.hayat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hayat.R
import com.hayat.data.ChatMessage
import com.hayat.data.ChatRole
import com.hayat.ui.viewmodel.ChatViewModel
import com.hayat.ui.theme.DarkGray
import com.hayat.ui.theme.Gray

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(LocalContext.current))
) {
    val messages by remember { derivedStateOf { viewModel.messages } }
    val isLoading by remember { derivedStateOf { viewModel.isLoading } }
    val loadingMessage by remember { derivedStateOf { viewModel.loadingMessage } }
    var textState by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Image picker launcher for vision analysis
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageUri ->
            viewModel.sendImageForAnalysis(imageUri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty() && !isLoading) {
                    item {
                        EmptyStatePromptSuggestions(
                            onSuggestionClicked = { suggestion ->
                                viewModel.sendMessage(suggestion)
                            }
                        )
                    }
                } else {
                    items(messages) { message ->
                        ChatBubble(message)
                    }
                }

                if (isLoading) {
                    item {
                        LoadingIndicator(loadingMessage)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Camera button for image analysis
                    IconButton(
                        onClick = {
                            if (!isLoading) {
                                imagePickerLauncher.launch("image/*")
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Take photo for analysis",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    TextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.type_message)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        enabled = !isLoading
                    )
                    IconButton(
                        onClick = {
                            if (textState.isNotBlank() && !isLoading) {
                                viewModel.sendMessage(textState)
                                textState = ""
                            }
                        },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else DarkGray,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 0.dp,
                bottomEnd = if (isUser) 0.dp else 12.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                )
                if (!isUser && !message.source.isNullOrBlank()) {
                    Text(
                        text = "Source: ${message.source}",
                        color = Color(0xFF66B366),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingIndicator(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = Gray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyStatePromptSuggestions(
    onSuggestionClicked: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.medical_assistant),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.ask_about_injuries),
            style = MaterialTheme.typography.bodyMedium,
            color = Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        PromptSuggestion(
            text = "What are the steps for treating a broken arm?",
            onClick = { onSuggestionClicked("What are the steps for treating a broken arm?") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        PromptSuggestion(
            text = "How to handle severe bleeding?",
            onClick = { onSuggestionClicked("How to handle severe bleeding?") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        PromptSuggestion(
            text = "First aid for burns",
            onClick = { onSuggestionClicked("First aid for burns") }
        )
    }
}

@Composable
fun PromptSuggestion(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = DarkGray
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}

class ChatViewModelFactory(private val context: android.content.Context) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(context) as T
    }
}
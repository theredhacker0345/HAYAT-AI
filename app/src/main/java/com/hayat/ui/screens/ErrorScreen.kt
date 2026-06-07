package com.hayat.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ErrorScreen(missingPath: String) {
    val context = LocalContext.current
    val hayatFolder = "/sdcard/Hayat/"
    val modelFile = "$hayatFolder/model.gguf"
    val embeddingFile = "$hayatFolder/e5-small.onnx"
    val dbFile = "$hayatFolder/medical.db"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "ملفات مفقودة",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "يرجى إنشاء المجلد التالي وإضافة الملفات المطلوبة:",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "المجلد: $hayatFolder",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "• model.gguf (~800MB-1.1GB): طراز الذكاء الاصطناعي للمعالجة الطبية",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Text(
                    text = "• e5-small.onnx (~100MB): نموذج التضمين للبحث الدلالي",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Text(
                    text = "• medical.db (~5-10MB): قاعدة البيانات الطبية",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "حجم الملفات قد يختلف حسب الإصدار. تأكد من تحميل النماذج المناسبة من الروابط في دليل الإعداد.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { openSetupGuide(context) },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("فتح دليل الإعداد")
        }
    }
}

private fun openSetupGuide(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://github.com/HayatAI/offline-medical-assistant#setup")
    }
    context.startActivity(intent)
}
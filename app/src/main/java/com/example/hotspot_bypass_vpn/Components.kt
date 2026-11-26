package com.example.hotspot_bypass_vpn

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

@Composable
fun ConflictCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF3D0000) else Color(0xFFFFEBEE)
        ),
        border = BorderStroke(1.dp, Color.Red)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, fontSize = 13.sp, color = Color(0xFFB71C1C), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SetupSteps(steps: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("HOW IT WORKS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        steps.forEachIndexed { index, step ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text("${index + 1}.", modifier = Modifier.width(26.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(step, modifier = Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusCard(title: String, isActive: Boolean, activeColor: Color, icon: ImageVector) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            // Uses theme surface color
            containerColor = if (isActive) activeColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(if (isActive) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) Color.White else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface // Adapts to Black/White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isActive) activeColor.copy(alpha = alpha) else Color.Gray))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "SERVICE RUNNING" else "NOT RUNNING",
                        color = if (isActive) activeColor else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DebugLogSection(logs: List<String>) {
    var showLogs by remember { mutableStateOf(false) }
    val logBgColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFE0E0E0)
    val logTextColor = if (isSystemInDarkTheme()) Color(0xFF00E676) else Color(0xFF1B5E20)

    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { showLogs = !showLogs }) {
            Icon(if (showLogs) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            Text(if (showLogs) "Hide Debug Logs" else "Show Debug Logs")
        }
        AnimatedVisibility(visible = showLogs) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(logBgColor, RoundedCornerShape(12.dp)) // Adaptive background
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    logs.asReversed().forEach { logLine ->
                        Text(text = "➜ $logLine", fontSize = 11.sp, color = logTextColor) // Adaptive text
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    val clipboardManager = LocalClipboardManager.current
    Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label.uppercase(), fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(
                value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface // Dynamic color
            )
        }
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(value))
            },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape) // Subtle contrast
                .size(36.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

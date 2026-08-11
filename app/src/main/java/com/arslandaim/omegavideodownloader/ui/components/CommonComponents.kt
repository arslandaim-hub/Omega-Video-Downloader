/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arslandaim.omegavideodownloader.SettingsManager
import kotlinx.coroutines.launch

@Composable
fun OmegaLogo(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = width * 0.15f
        
        drawArc(
            color = color,
            startAngle = 140f,
            sweepAngle = 260f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.05f),
            size = androidx.compose.ui.geometry.Size(width * 0.8f, height * 0.8f)
        )
        
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.05f, height * 0.85f),
            end = androidx.compose.ui.geometry.Offset(width * 0.35f, height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.65f, height * 0.85f),
            end = androidx.compose.ui.geometry.Offset(width * 0.95f, height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        val arrowStrokeWidth = strokeWidth * 0.8f
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.25f),
            end = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.35f, height * 0.48f),
            end = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(width * 0.65f, height * 0.48f),
            end = androidx.compose.ui.geometry.Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun PlatformBadge(name: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "badge_scale")
    
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    val questions = listOf(
        "What is your childhood nickname?",
        "What is your mother's name?",
        "What was your first pet's name?",
        "What city were you born in?"
    )
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("4-Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Security Question", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(securityQuestion.ifEmpty { "Select a Question" }, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        questions.forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q) },
                                onClick = {
                                    securityQuestion = q
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = securityAnswer,
                    onValueChange = { securityAnswer = it },
                    label = { Text("Your Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    "This will be used to recover your PIN if forgotten.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (pin.length == 4 && securityQuestion.isNotEmpty() && securityAnswer.isNotEmpty()) onConfirm(pin, securityQuestion, securityAnswer) },
                enabled = pin.length == 4 && securityQuestion.isNotEmpty() && securityAnswer.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun OptionSelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                LazyColumn(Modifier.selectableGroup()) {
                    items(options) { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (option == selectedOption),
                                    onClick = { onOptionSelected(option) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedOption),
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF1877F2))
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color(0xFF1877F2), modifier = Modifier.size(20.dp))
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        
        action()
    }
}

@Composable
fun ChangePinDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val securityQuestion by settingsManager.securityQuestion.collectAsState(initial = "")
    var securityAnswer by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var isVerified by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!isVerified) "Verify Security Question" else "Set New PIN") },
        text = {
            Column {
                if (!isVerified) {
                    Text(securityQuestion ?: "No security question set", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = securityAnswer,
                        onValueChange = { securityAnswer = it },
                        label = { Text("Your Answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("New 4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        if (!isVerified) {
                            if (settingsManager.verifySecurityAnswer(securityAnswer)) {
                                isVerified = true
                            } else {
                                android.widget.Toast.makeText(context, "Incorrect Answer", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (newPin.length == 4) {
                                settingsManager.updateLockerPin(newPin)
                                android.widget.Toast.makeText(context, "PIN Updated Successfully", android.widget.Toast.LENGTH_SHORT).show()
                                onSuccess()
                            }
                        }
                    }
                }
            ) {
                Text(if (!isVerified) "Verify" else "Update PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
            content = { Column(content = content) }
        )
    }
}

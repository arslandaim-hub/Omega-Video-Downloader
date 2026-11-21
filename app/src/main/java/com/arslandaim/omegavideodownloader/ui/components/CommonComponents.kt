/*
 * Omega Video Downloader Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.arslandaim.omegavideodownloader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arslandaim.omegavideodownloader.SettingsManager
import com.arslandaim.omegavideodownloader.ui.theme.OmegaTheme
import com.arslandaim.omegavideodownloader.ui.theme.ThemeColorPreset
import kotlinx.coroutines.launch

@Composable
fun OmegaLogo(modifier: Modifier = Modifier, color: Color = OmegaTheme.colors.primary) {
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
            topLeft = Offset(width * 0.1f, height * 0.05f),
            size = Size(width * 0.8f, height * 0.8f)
        )

        drawLine(
            color = color,
            start = Offset(width * 0.05f, height * 0.85f),
            end = Offset(width * 0.35f, height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = Offset(width * 0.65f, height * 0.85f),
            end = Offset(width * 0.95f, height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        val arrowStrokeWidth = strokeWidth * 0.8f
        drawLine(
            color = color,
            start = Offset(width * 0.5f, height * 0.25f),
            end = Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(width * 0.35f, height * 0.48f),
            end = Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(width * 0.65f, height * 0.48f),
            end = Offset(width * 0.5f, height * 0.62f),
            strokeWidth = arrowStrokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Circular split swatch displaying a single theme option composed of two harmonious colors.
 */
@Composable
fun DuoToneSwatch(
    preset: ThemeColorPreset,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val borderColor = OmegaTheme.colors.borderBright
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, borderColor, CircleShape)
    ) {
        val w = this.size.width
        val h = this.size.height

        drawPath(
            path = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(0f, h)
                close()
            },
            color = preset.primary
        )
        drawPath(
            path = Path().apply {
                moveTo(w, 0f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
            color = preset.secondary
        )
    }
}

@Composable
fun PlatformBadge(name: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "badge_scale")

    Surface(
        color = OmegaTheme.colors.card,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(0.8.dp, OmegaTheme.colors.border, RoundedCornerShape(14.dp))
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
            color = OmegaTheme.colors.textSecondary,
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
                    color = OmegaTheme.colors.textSecondary,
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
        containerColor = OmegaTheme.colors.cardElevated,
        title = { Text(title, fontWeight = FontWeight.Bold, color = OmegaTheme.colors.textPrimary) },
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
                                colors = RadioButtonDefaults.colors(selectedColor = OmegaTheme.colors.primary)
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                                color = OmegaTheme.colors.textPrimary
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
fun ThemeColorSelectionDialog(
    selectedPresetId: String,
    onDismiss: () -> Unit,
    onPresetSelected: (ThemeColorPreset) -> Unit
) {
    val activePreset = ThemeColorPreset.fromId(selectedPresetId)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OmegaTheme.colors.cardElevated,
        title = {
            Column {
                Text(
                    text = "Choose Theme Color",
                    fontWeight = FontWeight.Bold,
                    color = OmegaTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Each color blends a primary & accent tone across all screens",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmegaTheme.colors.textSecondary
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ThemeColorPreset.entries) { preset ->
                    val isSelected = preset == activePreset
                    Surface(
                        color = if (isSelected) preset.primary.copy(alpha = 0.14f) else OmegaTheme.colors.card,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 0.8.dp,
                            color = if (isSelected) preset.primary else OmegaTheme.colors.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(
                                selected = isSelected,
                                onClick = { onPresetSelected(preset) },
                                role = Role.RadioButton
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DuoToneSwatch(preset = preset, size = 34.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = OmegaTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = preset.colorPairLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OmegaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = preset.primary)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    action: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick?.invoke()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(OmegaTheme.colors.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = OmegaTheme.colors.primary, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = OmegaTheme.colors.textPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OmegaTheme.colors.textSecondary
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
    val context = LocalContext.current
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
            color = OmegaTheme.colors.textSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        Surface(
            color = OmegaTheme.colors.card,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.8.dp, OmegaTheme.colors.border, RoundedCornerShape(24.dp)),
            content = { Column(content = content) }
        )
    }
}

@Composable
fun SearchHistoryCard(
    title: String,
    history: List<String>,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onQueryClick: (String) -> Unit,
    onRemoveQuery: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    showPauseToggle: Boolean = true
) {
    val omegaColors = OmegaTheme.colors
    Surface(
        color = omegaColors.card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, omegaColors.border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = omegaColors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = omegaColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showPauseToggle) {
                        Surface(
                            onClick = onTogglePause,
                            color = if (isPaused) {
                                omegaColors.secondary.copy(alpha = 0.16f)
                            } else {
                                omegaColors.cardElevated
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                0.8.dp,
                                if (isPaused) omegaColors.secondary else omegaColors.borderBright
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPaused) {
                                        Icons.Default.PlayCircleOutline
                                    } else {
                                        Icons.Default.PauseCircleOutline
                                    },
                                    contentDescription = null,
                                    tint = if (isPaused) omegaColors.secondary else omegaColors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isPaused) "Resume" else "Pause",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPaused) omegaColors.secondary else omegaColors.textPrimary
                                )
                            }
                        }
                    }

                    if (history.isNotEmpty()) {
                        Surface(
                            onClick = onClearAll,
                            color = omegaColors.danger.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, omegaColors.danger.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear history",
                                    tint = omegaColors.danger,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Clear",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = omegaColors.danger
                                )
                            }
                        }
                    }
                }
            }

            if (isPaused) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Search history paused — new searches won't be saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = omegaColors.textSecondary
                )
            }

            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items = history, key = { it }) { query ->
                        Surface(
                            onClick = { onQueryClick(query) },
                            color = omegaColors.cardElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.7.dp, omegaColors.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = query,
                                    color = omegaColors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { onRemoveQuery(query) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = omegaColors.textSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (!isPaused) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your recent searches will appear here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = omegaColors.textSecondary.copy(alpha = 0.75f)
                )
            }
        }
    }
}

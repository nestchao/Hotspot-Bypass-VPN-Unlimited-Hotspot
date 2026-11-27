package com.example.hotspot_bypass_vpn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val ButtonShape = RoundedCornerShape(8.dp)
private val PillShape = RoundedCornerShape(percent = 50)

/** Draws a 1dp hairline along the bottom (and optionally top) edge. */
fun Modifier.hairlines(top: Boolean = false, bottom: Boolean = true, color: Color = AppColors.Divider): Modifier =
    drawBehind {
        val stroke = 1.dp.toPx()
        if (top) drawLine(color, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
        if (bottom) drawLine(color, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
    }

@Composable
fun LiveLabel(text: String = "Active") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.AccentSoft, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = AppColors.AccentSoft, fontSize = 12.sp, letterSpacing = 0.02.em, style = AppText.Action)
    }
}

/** Two equal, left-aligned underline tabs on a hairline. */
@Composable
fun ModeTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().hairlines()) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                    .drawBehind {
                        if (active) {
                            val h = 2.dp.toPx()
                            drawRect(AppColors.Accent, topLeft = Offset(0f, size.height - h), size = size.copy(height = h))
                        }
                    }
                    .padding(top = 8.dp, bottom = 13.dp)
            ) {
                Text(
                    label,
                    fontSize = 17.sp,
                    fontWeight = AppText.Section.fontWeight,
                    color = if (active) AppColors.Accent else AppColors.TabInactive
                )
            }
        }
    }
}

/** Pill-shaped two-option selector (e.g. Wi-Fi band). */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, AppColors.Outline, PillShape)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(48.dp)
                    .then(if (active) Modifier.border(1.dp, AppColors.Accent, PillShape) else Modifier)
                    .selectable(selected = active, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) AppColors.Accent else AppColors.TextDisabled
                )
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent,
            contentColor = AppColors.OnAccent,
            disabledContainerColor = AppColors.Accent.copy(alpha = 0.25f),
            disabledContentColor = AppColors.OnAccent.copy(alpha = 0.7f)
        ),
        modifier = modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(text, style = AppText.Button)
    }
}

/** Quiet outlined button used for stop/disconnect. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, AppColors.OutlineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
        modifier = modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(text, style = AppText.Button)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppText.Action,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 9.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = AppText.Section)
        action?.invoke()
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = AppText.Eyebrow, modifier = modifier.padding(bottom = 10.dp))
}

@Composable
private fun AccentIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = AppColors.Accent)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(19.dp))
    }
}

/** One row of the connection-details list: label over value, with copy (and reveal) actions. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    secret: Boolean = false,
    revealed: Boolean = true,
    onToggleReveal: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).hairlines(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(label.uppercase(), style = AppText.RowLabel)
            Spacer(Modifier.height(5.dp))
            val hidden = secret && !revealed
            Text(
                if (hidden) "••••••••" else value,
                style = AppText.RowValue,
                letterSpacing = if (hidden) 0.1.em else AppText.RowValue.letterSpacing
            )
        }
        if (secret) {
            AccentIconButton(
                if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                if (revealed) "Hide $label" else "Show $label",
                onToggleReveal
            )
        }
        AccentIconButton(Icons.Filled.ContentCopy, "Copy $label", onCopy)
    }
}

/** Label above a flat outlined text field. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    secret: Boolean = false,
    revealed: Boolean = true,
    onToggleReveal: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = AppText.FieldLabel)
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            isError = isError,
            shape = ButtonShape,
            textStyle = AppText.RowValue.copy(fontSize = 16.sp),
            placeholder = placeholder?.let { { Text(it, color = AppColors.TextDisabled) } },
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
            visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = if (secret) {
                {
                    IconButton(onClick = onToggleReveal) {
                        Icon(
                            if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealed) "Hide $label" else "Show $label",
                            tint = AppColors.Accent
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent,
                unfocusedBorderColor = AppColors.Outline,
                disabledBorderColor = AppColors.Divider,
                errorBorderColor = AppColors.Error,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                cursorColor = AppColors.Accent,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary,
                disabledTextColor = AppColors.TextDisabled,
            ),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
        )
        if (supportingText != null) {
            Spacer(Modifier.height(6.dp))
            Text(supportingText, style = AppText.Body, fontSize = 12.sp, color = if (isError) AppColors.Error else AppColors.TextSecondary)
        }
    }
}

/** Inline notice, e.g. when the other mode is already running. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, color: Color = AppColors.Warning) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = color, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = AppText.Body, color = color)
    }
}

/** Collapsible activity log with time column. */
@Composable
fun ActivityLog(entries: List<LogEntry>, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .hairlines()
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Activity log", style = AppText.Action, fontSize = 15.sp)
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide activity log" else "Show activity log",
                tint = AppColors.Accent
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                if (entries.isEmpty()) {
                    Text("Nothing yet.", style = AppText.Log)
                } else {
                    entries.asReversed().take(LOG_LINES_SHOWN).forEach { entry ->
                        Row(Modifier.padding(bottom = 6.dp)) {
                            Text(entry.time, style = AppText.Log, color = AppColors.AccentMuted, modifier = Modifier.width(48.dp))
                            Text(entry.message, style = AppText.Log)
                        }
                    }
                }
            }
        }
    }
}

private const val LOG_LINES_SHOWN = 60

/** Brief confirmation shown at the bottom of the screen (e.g. "Password copied"). */
@Composable
fun CopyToast(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        textAlign = TextAlign.Center,
        style = AppText.Action,
        color = AppColors.TextPrimary,
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Surface, ButtonShape)
            .border(1.dp, Color(0xFF314147), ButtonShape)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    )
}

/** Vertical gap helper to keep spacing values readable at call sites. */
@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))

package com.businesstracker.mileage.ui.smart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.domain.model.SmartClassificationMode
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
import com.businesstracker.mileage.ui.theme.SucculentPrimary

/**
 * Compact settings card for Smart Trip Recognition.
 * Follows the existing design system with pale-green icon container and inline toggle.
 */
@Composable
fun SmartClassificationCard(
    isEnabled: Boolean,
    currentMode: SmartClassificationMode,
    onToggleEnabled: (Boolean) -> Unit,
    onModeSelected: (SmartClassificationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showModeSelector by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Icon + Title/Subtitle + Inline Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SucculentPrimary.copy(alpha = 0.10f)
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = SucculentPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart classification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Learn from frequent trips",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    modifier = Modifier.semantics { contentDescription = "Smart classification" },
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SucculentPrimary,
                        checkedBorderColor = SucculentPrimary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // Mode Selector Row (Visible only when feature is enabled)
            AnimatedVisibility(
                visible = isEnabled,
                enter = expandVertically(animationSpec = tween(180)),
                exit = shrinkVertically(animationSpec = tween(180))
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showModeSelector = true }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Classification mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatModeLabel(currentMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = SucculentPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Change classification mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showModeSelector) {
        SmartClassificationModeBottomSheet(
            currentMode = currentMode,
            onModeSelected = { mode ->
                onModeSelected(mode)
                showModeSelector = false
            },
            onDismiss = { showModeSelector = false }
        )
    }
}

/**
 * Bottom sheet allowing user to choose between ALWAYS_ASK, AUTO_CLASSIFY, and SUGGEST_ONLY.
 * Uses standardized AppFloatingBottomSheet.
 */
@Composable
fun SmartClassificationModeBottomSheet(
    currentMode: SmartClassificationMode,
    onModeSelected: (SmartClassificationMode) -> Unit,
    onDismiss: () -> Unit
) {
    AppFloatingBottomSheet(
        onDismissRequest = onDismiss,
        title = "Classification mode"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeOptionRow(
                title = "Ask me first (Default)",
                description = "Suggest a category and ask for confirmation.",
                isSelected = currentMode == SmartClassificationMode.ALWAYS_ASK,
                onClick = { onModeSelected(SmartClassificationMode.ALWAYS_ASK) }
            )
            ModeOptionRow(
                title = "Automatic",
                description = "Automatically classify familiar trips when confidence is high.",
                isSelected = currentMode == SmartClassificationMode.AUTO_CLASSIFY,
                onClick = { onModeSelected(SmartClassificationMode.AUTO_CLASSIFY) }
            )
            ModeOptionRow(
                title = "Suggestions only",
                description = "Show suggestions without notifications or automatic changes.",
                isSelected = currentMode == SmartClassificationMode.SUGGEST_ONLY,
                onClick = { onModeSelected(SmartClassificationMode.SUGGEST_ONLY) }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeOptionRow(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) SucculentPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, SucculentPrimary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            if (isSelected) {
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SucculentPrimary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatModeLabel(mode: SmartClassificationMode): String {
    return when (mode) {
        SmartClassificationMode.ALWAYS_ASK -> "Ask me first"
        SmartClassificationMode.AUTO_CLASSIFY -> "Automatic"
        SmartClassificationMode.SUGGEST_ONLY -> "Suggestions only"
    }
}

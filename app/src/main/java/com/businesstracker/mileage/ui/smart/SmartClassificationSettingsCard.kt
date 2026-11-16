package com.businesstracker.mileage.ui.smart

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
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.businesstracker.mileage.domain.model.SmartClassificationMode
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
import com.businesstracker.mileage.ui.theme.SucculentPrimary

/**
 * Compact redesigned settings card for Smart Trip Recognition.
 * Matches the final approved mockup:
 * - Rounded white card with mint-green sparkle icon
 * - Navy title "Smart classification" with the selected mode directly underneath in muted gray
 * - Vertically aligned right-facing chevron
 * - Entire card is tappable to open the Classification mode bottom sheet
 * - No toggle, no divider, no extra description
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable { showModeSelector = true },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Smart classification",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (!isEnabled) "Disable" else formatModeLabel(currentMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Change classification mode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showModeSelector) {
        SmartClassificationModeBottomSheet(
            isEnabled = isEnabled,
            currentMode = currentMode,
            onSelectDisable = {
                onToggleEnabled(false)
                showModeSelector = false
            },
            onSelectMode = { mode ->
                if (!isEnabled) {
                    onToggleEnabled(true)
                }
                onModeSelected(mode)
                showModeSelector = false
            },
            onDismiss = { showModeSelector = false }
        )
    }
}

/**
 * Bottom sheet displaying the 4 options:
 * 1. Disable
 * 2. Ask me first
 * 3. Automatic
 * 4. Suggestions only
 *
 * Uses the app's existing radio-button styling with no green background or colored pills.
 */
@Composable
fun SmartClassificationModeBottomSheet(
    isEnabled: Boolean,
    currentMode: SmartClassificationMode,
    onSelectDisable: () -> Unit,
    onSelectMode: (SmartClassificationMode) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Option 1: Disable
            ClassificationRadioOption(
                label = "Disable",
                selected = !isEnabled,
                onClick = onSelectDisable
            )

            // Option 2: Ask me first
            ClassificationRadioOption(
                label = "Ask me first",
                selected = isEnabled && currentMode == SmartClassificationMode.ALWAYS_ASK,
                onClick = { onSelectMode(SmartClassificationMode.ALWAYS_ASK) }
            )

            // Option 3: Automatic
            ClassificationRadioOption(
                label = "Automatic",
                selected = isEnabled && currentMode == SmartClassificationMode.AUTO_CLASSIFY,
                onClick = { onSelectMode(SmartClassificationMode.AUTO_CLASSIFY) }
            )

            // Option 4: Suggestions only
            ClassificationRadioOption(
                label = "Suggestions only",
                selected = isEnabled && currentMode == SmartClassificationMode.SUGGEST_ONLY,
                onClick = { onSelectMode(SmartClassificationMode.SUGGEST_ONLY) }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ClassificationRadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) SucculentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

fun formatModeLabel(mode: SmartClassificationMode): String {
    return when (mode) {
        SmartClassificationMode.ALWAYS_ASK -> "Ask me first"
        SmartClassificationMode.AUTO_CLASSIFY -> "Automatic"
        SmartClassificationMode.SUGGEST_ONLY -> "Suggestions only"
    }
}

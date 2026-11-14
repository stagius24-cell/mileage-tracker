package com.businesstracker.mileage.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.ui.theme.AppDimensions
import com.businesstracker.mileage.ui.theme.AppShapes
import com.businesstracker.mileage.ui.theme.AppSpacing
import com.businesstracker.mileage.ui.theme.SucculentPrimary

/**
 * Standardized Floating Card for main screens (Section 7).
 *
 * Implements 20dp corner radius, 100% opaque surface, and subtle border.
 */
@Composable
fun AppFloatingCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = if (onClick != null) {
            modifier
                .fillMaxWidth()
                .clip(AppShapes.FloatingCard)
                .clickable(onClick = onClick)
        } else {
            modifier.fillMaxWidth()
        },
        shape = AppShapes.FloatingCard,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = border ?: BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        content = content
    )
}

/**
 * Standardized settings row for floating cards.
 */
@Composable
fun AppSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = SucculentPrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val isTwoLine = subtitle != null
    val minHeight = if (isTwoLine) AppDimensions.SettingsRowTwoLineMinHeight else AppDimensions.SettingsRowMinHeight

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppSpacing.Large, vertical = AppSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = AppShapes.IconContainer,
                color = iconTint.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(AppSpacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.Medium))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(AppSpacing.Small))
            trailing()
        }
    }
}

/**
 * Standardized section header.
 */
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.ExtraSmall, vertical = AppSpacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

/**
 * Standardized 50–52dp Primary Action Button.
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = AppDimensions.PrimaryButtonHeight),
        shape = AppShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = SucculentPrimary,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = AppSpacing.Large, vertical = AppSpacing.Small)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(AppSpacing.Small))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

/**
 * Standardized Secondary Action Button.
 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = AppDimensions.PrimaryButtonHeight),
        shape = AppShapes.Button,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = AppSpacing.Large, vertical = AppSpacing.Small)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(AppSpacing.Small))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

/**
 * Standardized Segmented Control with 12dp radius and 48dp minimum touch target.
 */
@Composable
fun AppSegmentedControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconFor: ((String) -> ImageVector)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Small)
    ) {
        options.forEach { (key, label) ->
            val active = selected.equals(key, ignoreCase = true)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = AppDimensions.SelectionControlHeight)
                    .clip(AppShapes.SegmentedControl)
                    .clickable { onSelect(key) }
                    .semantics { role = Role.Button },
                shape = AppShapes.SegmentedControl,
                color = if (active) SucculentPrimary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimensions.SelectionControlHeight)
                        .padding(horizontal = AppSpacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (iconFor != null) {
                        Icon(
                            imageVector = iconFor(key),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(AppSpacing.ExtraSmall))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

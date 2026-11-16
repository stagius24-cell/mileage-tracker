package com.businesstracker.mileage.ui.smart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
import com.businesstracker.mileage.ui.theme.PersonalBlue
import com.businesstracker.mileage.ui.theme.SucculentPrimary

/**
 * Reusable confirmation bottom sheet for Smart Trip Recognition.
 * Accessible from Trip History and Trip Details.
 */
@Composable
fun SmartClassificationConfirmationSheet(
    tripId: String,
    startAddress: String?,
    endAddress: String?,
    suggestedCategory: String,
    confirmedCount: Int,
    suggestedEntity: String? = null,
    suggestedPurpose: String? = null,
    onConfirm: (category: String, entity: String?, purpose: String?) -> Unit,
    onChooseOpposite: (oppositeCategory: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isBusiness = suggestedCategory.equals("BUSINESS", ignoreCase = true)
    val oppositeCategory = if (isBusiness) "PERSONAL" else "BUSINESS"
    val oppositeDisplay = if (isBusiness) "Personal" else "Business"
    val accentColor = if (isBusiness) SucculentPrimary else PersonalBlue

    AppFloatingBottomSheet(
        onDismissRequest = onDismiss,
        title = "Classify this trip"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Based on your previous trips",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Route Details Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = SucculentPrimary, modifier = Modifier.size(10.dp)) {}
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = startAddress?.takeIf { it.isNotBlank() } ?: "Starting location",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Color(0xFFEF4444), modifier = Modifier.size(10.dp)) {}
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = endAddress?.takeIf { it.isNotBlank() } ?: "Destination",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Suggestion Summary Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Suggested: ${if (isBusiness) "Business" else "Personal"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                    Text(
                        text = "Based on $confirmedCount matching confirmed trips on this route.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isBusiness && (!suggestedEntity.isNullOrBlank() || !suggestedPurpose.isNullOrBlank())) {
                        val details = listOfNotNull(
                            suggestedEntity?.takeIf { it.isNotBlank() },
                            suggestedPurpose?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onConfirm(suggestedCategory, suggestedEntity, suggestedPurpose) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = if (isBusiness) Icons.Outlined.BusinessCenter else Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Confirm ${if (isBusiness) "Business" else "Personal"}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { onChooseOpposite(oppositeCategory) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Classify as $oppositeDisplay",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(
                        text = "Not now",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

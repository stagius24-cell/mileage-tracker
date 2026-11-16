package com.businesstracker.mileage.ui.smart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.businesstracker.mileage.ui.theme.PersonalBlue
import com.businesstracker.mileage.ui.theme.SucculentPrimary

/**
 * Reusable smart classification suggestion component for Trip Details and Trip History.
 * Clearly differentiates pending suggestions from confirmed classifications.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartTripSuggestionCard(
    suggestedCategory: String,
    confirmedCount: Int,
    suggestedEntity: String? = null,
    suggestedPurpose: String? = null,
    onConfirm: () -> Unit,
    onChangeToOpposite: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBusiness = suggestedCategory.equals("BUSINESS", ignoreCase = true)
    val oppositeCategory = if (isBusiness) "PERSONAL" else "BUSINESS"
    val oppositeDisplay = if (isBusiness) "Personal" else "Business"

    val accentColor = if (isBusiness) SucculentPrimary else PersonalBlue
    val cardBg = if (isBusiness) SucculentPrimary.copy(alpha = 0.06f) else PersonalBlue.copy(alpha = 0.06f)
    val borderColor = if (isBusiness) SucculentPrimary.copy(alpha = 0.35f) else PersonalBlue.copy(alpha = 0.35f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Sparkle Icon + "Suggested: Business/Personal" badge + Dismiss Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Suggested: ${if (isBusiness) "Business" else "Personal"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss suggestion",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Supporting Evidence Text
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Based on $confirmedCount similar trips",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!suggestedPurpose.isNullOrBlank() || !suggestedEntity.isNullOrBlank()) {
                    val details = listOfNotNull(
                        suggestedEntity?.takeIf { it.isNotBlank() },
                        suggestedPurpose?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions: Confirm | Change to Opposite | Dismiss
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = if (isBusiness) Icons.Outlined.BusinessCenter else Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Confirm ${if (isBusiness) "Business" else "Personal"}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { onChangeToOpposite(oppositeCategory) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Change to $oppositeDisplay",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

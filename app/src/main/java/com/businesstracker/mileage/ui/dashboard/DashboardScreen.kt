package com.businesstracker.mileage.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.ui.components.SwipeableTripCard
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToReports: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var monthMenuExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // MileIQ-Style Header Bar (Month Picker + Document Icon & 3-Stat Bar)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Month Dropdown in Center, Document Icon on Right
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Center: Month Selector
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { monthMenuExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.selectedMonthName.ifEmpty { "November" },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Month",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )

                        DropdownMenu(
                            expanded = monthMenuExpanded,
                            onDismissRequest = { monthMenuExpanded = false }
                        ) {
                            state.availableMonths.forEach { monthIso ->
                                val date = try {
                                    LocalDate.parse("$monthIso-01")
                                } catch (_: Exception) {
                                    LocalDate.now()
                                }
                                val name = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                                DropdownMenuItem(
                                    text = { Text("$name ${date.year}") },
                                    onClick = {
                                        viewModel.selectMonth(monthIso)
                                        monthMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Right: Reports Document Icon
                    IconButton(
                        onClick = { onNavigateToReports?.invoke() },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Reports & Tax Summary",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3-Stat Bar (DRIVES | MILES DRIVEN (Odometer) | LOGGED ($))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. DRIVES
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = state.monthDrivesCount.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "DRIVES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SlateNeutral
                        )
                    }

                    // 2. MILES DRIVEN (Odometer style: light gray leading zeroes + bold digits)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1.3f)
                    ) {
                        OdometerMilesDisplay(miles = state.monthMiles)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "MILES DRIVEN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SlateNeutral
                        )
                    }

                    // 3. LOGGED ($ in green)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "\$${String.format(Locale.US, "%.0f", state.monthDeduction)}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = SucculentPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "LOGGED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SlateNeutral
                        )
                    }
                }
            }
        }

        // Section: Trips to Review (MileIQ Style)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trips to Review",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (state.unreviewedTrips.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = state.unreviewedTrips.size.toString(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (state.unreviewedTrips.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SucculentPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "All Drives Classified!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto-detected drives will appear here for swipe classification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        } else {
            items(state.unreviewedTrips, key = { it.id }) { trip ->
                SwipeableTripCard(
                    trip = trip,
                    onClassifyBusiness = { id, entity, purpose ->
                        viewModel.classifyTripAsBusiness(id, entity, purpose)
                    },
                    onClassifyPersonal = { id ->
                        viewModel.classifyTripAsPersonal(id)
                    }
                )
            }
        }

        // Section: Classification Breakdown (MySucculentCorner vs Personal)
        item {
            Text(
                text = "Mileage Breakdown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            val totalLogged = (state.monthMiles + state.personalMiles).coerceAtLeast(0.1)
            val businessFraction = (state.monthMiles / totalLogged).toFloat()
            val personalFraction = 1f - businessFraction

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Proportional Mileage Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    ) {
                        if (businessFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(businessFraction.coerceAtLeast(0.01f))
                                    .fillMaxSize()
                                    .background(SucculentPrimary)
                            )
                        }
                        if (personalFraction > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .weight(personalFraction.coerceAtLeast(0.01f))
                                    .fillMaxSize()
                                    .background(SlateNeutral.copy(alpha = 0.4f))
                            )
                        }
                    }

                    // MySucculentCorner Business Row
                    BreakdownRow(
                        title = "MySucculentCorner (Deductible)",
                        miles = state.monthMiles,
                        deduction = state.monthDeduction,
                        color = SucculentPrimary,
                        icon = Icons.Default.LocalFlorist
                    )

                    // Personal Excluded Row
                    BreakdownRow(
                        title = "Personal (Non-Deductible)",
                        miles = state.personalMiles,
                        deduction = 0.0,
                        color = SlateNeutral,
                        icon = Icons.Default.DirectionsCar
                    )
                }
            }
        }
    }
}

@Composable
private fun OdometerMilesDisplay(
    miles: Double,
    modifier: Modifier = Modifier
) {
    val totalInt = kotlin.math.round(miles).toInt().coerceAtLeast(0)
    val formatted = String.format(Locale.US, "%06d", totalInt)
    val firstNonZero = formatted.indexOfFirst { it != '0' }.let { if (it == -1) formatted.length - 1 else it }
    val leadingZeros = formatted.substring(0, firstNonZero)
    val activeDigits = formatted.substring(firstNonZero)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leadingZeros.isNotEmpty()) {
            Text(
                text = leadingZeros,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
            )
        }
        Text(
            text = activeDigits,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun BreakdownRow(
    title: String,
    miles: Double,
    deduction: Double,
    color: Color,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(6.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", miles)} miles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Text(
            text = if (deduction > 0) "\$${String.format(Locale.US, "%,.2f", deduction)}" else "–",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (deduction > 0) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

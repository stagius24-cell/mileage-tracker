package com.businesstracker.mileage.ui.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import com.businesstracker.mileage.ui.theme.SucculentSecondary
import java.util.Locale

@Composable
fun ReportsExportScreen(
    viewModel: ReportsExportViewModel = hiltViewModel(),
    onNavigateToTrips: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var yearDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: Year Selector & Export Buttons (CSV + PDF)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Year Selector / Header Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { yearDropdownExpanded = true }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${state.selectedYear} Reports",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Year",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )

                        DropdownMenu(
                            expanded = yearDropdownExpanded,
                            onDismissRequest = { yearDropdownExpanded = false }
                        ) {
                            state.availableYears.forEach { yr ->
                                DropdownMenuItem(
                                    text = { Text("$yr Tax Year") },
                                    onClick = {
                                        viewModel.selectYear(yr)
                                        yearDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Export Buttons Row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.exportToCsv { intent ->
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CSV", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                viewModel.exportToPdf { intent ->
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                }

                // Filter Chips (Entity & Category)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val entities = listOf("ALL" to "All Entities", "MySucculentCorner" to "MySucculentCorner", "Personal" to "Personal")
                    items(entities) { (key, label) ->
                        val isSelected = state.selectedBusinessEntity == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectBusinessEntity(key) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SucculentPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val categories = listOf("ALL" to "All Drives", "BUSINESS" to "Business", "PERSONAL" to "Personal", "UNREVIEWED" to "Unreviewed")
                    items(categories) { (key, label) ->
                        val isSelected = state.selectedClassification == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectClassification(key) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // Comprehensive Metrics Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Filtered Summary Totals",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("DEDUCTION", style = MaterialTheme.typography.labelSmall, color = SlateNeutral, fontWeight = FontWeight.Bold)
                            Text(
                                text = "\$${String.format(Locale.US, "%,.2f", state.totalDeduction)}",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, color = SucculentPrimary)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TOTAL MILES", style = MaterialTheme.typography.labelSmall, color = SlateNeutral, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", state.totalMiles)} mi",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Business Miles", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text("${String.format(Locale.US, "%.1f", state.businessMiles)} mi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("Personal Miles", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text("${String.format(Locale.US, "%.1f", state.personalMiles)} mi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("Avg Distance", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text("${String.format(Locale.US, "%.1f", state.averageDistance)} mi", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Drives", style = MaterialTheme.typography.labelSmall, color = SlateNeutral)
                            Text("${state.totalTrips}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Entity Breakdown Card
        if (state.entityBreakdown.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Business Entity Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        state.entityBreakdown.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (item.entityName == "MySucculentCorner") Icons.Default.LocalFlorist else Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = if (item.entityName == "MySucculentCorner") SucculentPrimary else SlateNeutral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(item.entityName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", item.totalMiles)} mi  (\$${String.format(Locale.US, "%.2f", item.totalDeduction)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Yearly Overview & Interactive Mileage/Money Chart Card
        item {
            YearlyStatsChartCard(
                year = state.selectedYear,
                ytdLogged = state.ytdLoggedDeduction,
                ytdPotential = state.ytdPotentialDeduction,
                ytdMiles = state.ytdBusinessMiles,
                ytdTotalMiles = state.ytdTotalMiles,
                monthlySummaries = state.monthlySummaries
            )
        }

        // Monthly Summary Cards (MileIQ Style as shown in reference)
        items(state.monthlySummaries, key = { it.monthIso }) { summary ->
            MonthlySummaryCard(
                summary = summary,
                onExportMonth = {
                    viewModel.exportToCsv(summary.monthIso) { intent ->
                        context.startActivity(intent)
                    }
                },
                onViewMonthDrives = {
                    onNavigateToTrips?.invoke(summary.monthIso)
                }
            )
        }
    }
}

@Composable
private fun YearlyStatsChartCard(
    year: String,
    ytdLogged: Double,
    ytdPotential: Double,
    ytdMiles: Double,
    ytdTotalMiles: Double,
    monthlySummaries: List<MonthSummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = SucculentPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = SucculentPrimary,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$year YTD Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${String.format(Locale.US, "%.0f", ytdMiles)} mi logged",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateNeutral,
                    fontWeight = FontWeight.Bold
                )
            }

            // Financial comparison: Logged vs Potential
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "\$${String.format(Locale.US, "%,.2f", ytdLogged)}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = SucculentPrimary
                    )
                    Text(
                        text = "LOGGED DEDUCTION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SlateNeutral
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "\$${String.format(Locale.US, "%,.2f", ytdPotential)}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "TOTAL POTENTIAL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SlateNeutral
                    )
                }
            }

            // Visual Month-by-Month Chart Bar
            if (monthlySummaries.isNotEmpty()) {
                val chartMonths = monthlySummaries.take(6).reversed()
                val maxMonthPotential = chartMonths.maxOfOrNull { it.potentialDeduction }.let {
                    if (it == null || it <= 0.0) 100.0 else it
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MONTHLY DEDUCTION TREND",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SlateNeutral
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        chartMonths.forEach { item ->
                            val loggedFraction = (item.loggedDeduction / maxMonthPotential).toFloat().coerceIn(0.05f, 1f)
                            val potentialFraction = (item.potentialDeduction / maxMonthPotential).toFloat().coerceIn(0.05f, 1f)
                            val shortName = if (item.monthName.length >= 3) item.monthName.substring(0, 3) else item.monthName

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Stacked Bar
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height((70 * potentialFraction).dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SucculentSecondary.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((70 * loggedFraction).dp)
                                            .background(SucculentPrimary)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = shortName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = SlateNeutral
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryCard(
    summary: MonthSummary,
    onExportMonth: () -> Unit,
    onViewMonthDrives: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Row 1: Month Title & Logged Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = summary.monthName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (summary.unreviewedDrives > 0) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = "${summary.totalDrives} Drives • ${String.format(Locale.US, "%.0f", summary.totalMiles)} Miles",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (summary.unreviewedDrives > 0) Color(0xFFDC2626) else SlateNeutral
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "\$${String.format(Locale.US, "%.2f", summary.loggedDeduction)}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = SucculentPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (summary.percentComplete >= 100) "100% Complete" else "${summary.percentComplete}% Complete",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (summary.percentComplete >= 100) SucculentPrimary else SlateNeutral
                    )
                }
            }

            // Row 2: Dual-Color Progress Bar (Dark Green = Logged, Light Green = Potential)
            val loggedFrac = if (summary.potentialDeduction > 0) {
                (summary.loggedDeduction / summary.potentialDeduction).toFloat().coerceIn(0.01f, 1f)
            } else 1f
            val potentialFrac = 1f - loggedFrac

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (loggedFrac > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(loggedFrac.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(SucculentPrimary)
                    )
                }
                if (potentialFrac > 0.001f) {
                    Box(
                        modifier = Modifier
                            .weight(potentialFrac.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(SucculentSecondary)
                    )
                }
            }

            // Row 3: Logged vs Potential Readouts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "\$${String.format(Locale.US, "%.2f", summary.loggedDeduction)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = SucculentPrimary
                    )
                    Text(
                        text = "logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateNeutral
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "\$${String.format(Locale.US, "%.2f", summary.potentialDeduction)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "potential",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateNeutral
                    )
                }
            }

            // Collapsible / Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExportMonth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text(
                        text = "Submit ${summary.monthName} Report",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = onViewMonthDrives,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "View ${summary.monthName} Drives",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

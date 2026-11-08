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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reports",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

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

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    item {
                        CompactFilterMenu(
                            label = state.selectedYear,
                            options = state.availableYears.map { it to "$it tax year" },
                            onSelect = viewModel::selectYear
                        )
                    }
                    item {
                        CompactFilterMenu(
                            label = when (state.selectedBusinessEntity) {
                                "ALL" -> "All entities"
                                else -> state.selectedBusinessEntity
                            },
                            options = listOf(
                                "ALL" to "All entities",
                                "MySucculentCorner" to "Business",
                                "Personal" to "Personal"
                            ),
                            onSelect = viewModel::selectBusinessEntity
                        )
                    }
                    item {
                        CompactFilterMenu(
                            label = when (state.selectedClassification) {
                                "ALL" -> "All drives"
                                "BUSINESS" -> "Business"
                                "PERSONAL" -> "Personal"
                                else -> "Unreviewed"
                            },
                            options = listOf(
                                "ALL" to "All drives",
                                "BUSINESS" to "Business",
                                "PERSONAL" to "Personal",
                                "UNREVIEWED" to "Unreviewed"
                            ),
                            onSelect = viewModel::selectClassification
                        )
                    }
                }
            }
        }

        item {
            YearlyStatsChartCard(
                year = state.selectedYear,
                ytdLogged = state.totalDeduction,
                totalMiles = state.totalMiles,
                drivesCount = state.totalTrips,
                monthlySummaries = state.monthlySummaries
            )
        }

        item {
            MonthlyDetailsSection(
                summaries = state.monthlySummaries,
                onExportMonth = { monthIso ->
                    viewModel.exportToCsv(monthIso) { intent -> context.startActivity(intent) }
                },
                onViewMonthDrives = { monthIso ->
                    onNavigateToTrips?.invoke(monthIso)
                }
            )
        }
    }
}

@Composable
private fun CompactFilterMenu(
    label: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun YearlyStatsChartCard(
    year: String,
    ytdLogged: Double,
    totalMiles: Double,
    drivesCount: Int,
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
            // Header with year and drives count
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
                        text = "$year Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "$drivesCount drives",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateNeutral,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Financial & Mileage Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "\$${String.format(Locale.US, "%,.2f", ytdLogged)}",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = SucculentPrimary
                    )
                    Text(
                        text = "TOTAL DEDUCTION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = SlateNeutral
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", totalMiles)} mi",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "TOTAL MILES",
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
private fun MonthlyDetailsSection(
    summaries: List<MonthSummary>,
    onExportMonth: (String) -> Unit,
    onViewMonthDrives: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "monthly_details_rotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Monthly details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (expanded) "Tap a month for actions" else "${summaries.size} months hidden",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide monthly details" else "Show monthly details",
                    modifier = Modifier.rotate(rotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    summaries.forEachIndexed { index, summary ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        CompactMonthRow(
                            summary = summary,
                            onExportMonth = { onExportMonth(summary.monthIso) },
                            onViewMonthDrives = { onViewMonthDrives(summary.monthIso) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMonthRow(
    summary: MonthSummary,
    onExportMonth: () -> Unit,
    onViewMonthDrives: () -> Unit
) {
    var expanded by remember(summary.monthIso) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(150),
        label = "month_rotation_${summary.monthIso}"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        summary.monthName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (summary.unreviewedDrives > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Unreviewed drives",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    "${summary.totalDrives} drives • ${String.format(Locale.US, "%.1f", summary.totalMiles)} mi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "\$${String.format(Locale.US, "%.2f", summary.loggedDeduction)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = SucculentPrimary
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val loggedFraction = if (summary.potentialDeduction > 0.0) {
                    (summary.loggedDeduction / summary.potentialDeduction).toFloat().coerceIn(0f, 1f)
                } else 1f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loggedFraction)
                            .height(8.dp)
                            .background(SucculentPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MonthMetric("Potential", "\$${String.format(Locale.US, "%.2f", summary.potentialDeduction)}")
                    MonthMetric("Complete", "${summary.percentComplete}%", Alignment.CenterHorizontally)
                    MonthMetric("Unreviewed", summary.unreviewedDrives.toString(), Alignment.End)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportMonth,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Export CSV")
                    }
                    Button(
                        onClick = onViewMonthDrives,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                    ) {
                        Text("View drives")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthMetric(
    label: String,
    value: String,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

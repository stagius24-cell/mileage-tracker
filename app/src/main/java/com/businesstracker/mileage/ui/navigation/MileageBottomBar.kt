package com.businesstracker.mileage.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.businesstracker.mileage.ui.theme.SucculentPrimary

@Composable
fun MileageBottomBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route
    val currentBaseRoute = currentRoute?.substringBefore("?")

    val selectedIndex = Screen.bottomNavItems.indexOfFirst { it.route == currentBaseRoute }.let {
        if (it == -1) 0 else it
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.background else Color(0xFFF3F6FA))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val itemCount = Screen.bottomNavItems.size
                val tabWidth = maxWidth / itemCount

                // Animated capsule indicator that glides/moves smoothly across tabs with slower easing
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = tween(
                        durationMillis = 650,
                        easing = FastOutSlowInEasing
                    ),
                    label = "capsule_indicator_offset"
                )

                // Capsule pill shape indicator
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .background(
                            color = SucculentPrimary.copy(alpha = 0.11f),
                            shape = RoundedCornerShape(14.dp)
                        )
                )

                // Tabs
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Screen.bottomNavItems.forEachIndexed { index, screen ->
                        val isSelected = selectedIndex == index

                        val itemColor by animateColorAsState(
                            targetValue = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                            label = "tab_color_$index"
                        )

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1.0f,
                            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                            label = "tab_scale_$index"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val isCurrent = currentBaseRoute == screen.route
                                    val startDestId = navController.graph.findStartDestination().id
                                    if (!isCurrent) {
                                        navController.navigate(screen.route) {
                                            popUpTo(startDestId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = screen != Screen.History
                                        }
                                    } else if (screen == Screen.History) {
                                        // Reset to top-level clean History if already on History (e.g. filtered view)
                                        navController.navigate(Screen.History.route) {
                                            popUpTo(startDestId) {
                                                saveState = false
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = itemColor,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = itemColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

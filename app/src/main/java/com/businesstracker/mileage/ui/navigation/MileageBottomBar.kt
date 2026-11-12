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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
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
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .background(
                            color = SucculentPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(percent = 50)
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
                                .clip(RoundedCornerShape(percent = 50))
                                .clickable {
                                    if (currentBaseRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Dashboard.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            // A saved History stack can include Settings opened from a trip.
                                            restoreState = screen != Screen.History
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

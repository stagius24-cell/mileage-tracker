package com.businesstracker.mileage.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Centralized shape tokens specifying corner radii across all surfaces and controls.
 */
object AppShapes {
    /** 20dp radius for all floating cards on main screens */
    val FloatingCard = RoundedCornerShape(20.dp)

    /** 24dp top corners for all floating bottom sheets */
    val BottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** 20dp radius on all four sides for centered floating confirmation dialogs */
    val FloatingDialog = RoundedCornerShape(20.dp)

    /** 12dp radius for all input fields */
    val Input = RoundedCornerShape(12.dp)

    /** 12dp radius for primary and secondary action buttons */
    val Button = RoundedCornerShape(12.dp)

    /** 12dp radius for segmented controls, filter chips, and pill selectors */
    val SegmentedControl = RoundedCornerShape(12.dp)

    /** 12dp radius for small icon containers and badges */
    val IconContainer = RoundedCornerShape(12.dp)

    /** 2dp radius for bottom sheet drag handle */
    val DragHandle = RoundedCornerShape(2.dp)
}

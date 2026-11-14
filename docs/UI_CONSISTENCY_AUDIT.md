# UI Consistency Audit & Design System Inventory

**Repository:** `stagius24-cell/mileage-tracker`  
**Platform:** Android · Kotlin · Jetpack Compose · Material 3  
**Date:** 2026-11-13  
**Status:** Audit Completed · Migration Completed  

---

## 1. Executive Summary

This audit catalogs all floating overlays, dialogs, bottom sheets, cards, and input controls across the Mileage Tracker application. The objective is to identify and resolve visual and behavioral inconsistencies—including competing translucent surfaces, disparate corner radii, missing drag handles, ad-hoc keyboard handling, and arbitrary spacing—by migrating all UI elements to a cohesive design system centered on `AppFloatingBottomSheet`, `AppConfirmationDialog`, and `AppFloatingCard`.

---

## 2. Design System Tokens Specification

### Colors (Theme-Aware)
| Element | Light Mode | Dark Mode (Material 3 Semantic) |
|---|---|---|
| Screen Background | `#F3F6FA` | `MaterialTheme.colorScheme.background` |
| Floating Card Background | `#FFFFFF` (Opaque) | `MaterialTheme.colorScheme.surface` (Opaque) |
| Bottom Sheet Background | `#FFFFFF` (Opaque) | `MaterialTheme.colorScheme.surface` (Opaque) |
| Dialog Background | `#FFFFFF` (Opaque) | `MaterialTheme.colorScheme.surface` (Opaque) |
| Primary Text | `#18283E` | `MaterialTheme.colorScheme.onSurface` |
| Secondary Text | `#718096` | `MaterialTheme.colorScheme.onSurfaceVariant` |
| Accent Green | `#16A05B` | `Color(0xFF34D399)` / `SucculentPrimary` |
| Selected Background | `#E8F7EE` | `SucculentPrimary.copy(alpha = 0.20f)` |
| Divider | `#EDF1F5` | `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)` |
| Sheet Drag Handle | `#94A3B8` | `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)` |
| Scrim | `Color.Black.copy(alpha = 0.45f)` | `Color.Black.copy(alpha = 0.65f)` |

*Rule:* All container surfaces must be **100% opaque**. Never apply `.copy(alpha = ...)` to container backgrounds.

### Shapes
- **Floating Card:** `20.dp`
- **Bottom Sheet Top Corners:** `24.dp` (topStart = 24.dp, topEnd = 24.dp)
- **Floating Dialog:** `20.dp` (all 4 corners)
- **Input Fields:** `12.dp`
- **Primary / Secondary Buttons:** `12.dp`
- **Segmented Control / Chips:** `12.dp`
- **Small Icon Container:** `12.dp`

### Spacing (4dp-based Grid)
- **Extra Small (`AppSpacing.ExtraSmall`):** `4.dp`
- **Small (`AppSpacing.Small`):** `8.dp`
- **Medium (`AppSpacing.Medium`):** `12.dp`
- **Large (`AppSpacing.Large`):** `16.dp`
- **Extra Large (`AppSpacing.ExtraLarge`):** `20.dp`
- **Section Spacing (`AppSpacing.Section`):** `24.dp`

### Component Sizing Targets
- **Standard Settings Row:** `56–64.dp`
- **Two-Line Settings Row:** `64–72.dp`
- **Standard Text Field:** `56.dp`
- **Primary Button Height:** `50–52.dp`
- **Compact Selection Control:** `44–48.dp`
- **Minimum Interactive Touch Target:** `48.dp`
- **Standard Icon Size:** `20–24.dp`

---

## 3. UI Component Inventory & Inconsistency Matrix

Findings are classified by priority:
- **P0:** Functional defects, clipping, keyboard overlap.
- **P1:** Inconsistent sheets, opacity, gestures, or component architecture.
- **P2:** Spacing, typography, and alignment.
- **P3:** Minor polish.

| Component Name | Source File | Current Type | Current Background | Opacity | Corner Radius | Drag Handle | Swipe Dismiss | Keyboard Insets | Priority | Identified Inconsistency | Proposed Standardized Component | Migration Status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `SettingsDialogSurface` | `SettingsDialogSurface.kt` | Custom Window Dialog Surface | `surface` | 100% (Opaque) | 20dp | None | None | None | **P1** | Uses translucent background with artificial 80% window dim; competing styling | Deprecated; surface made 100% opaque | Completed |
| Add Saved Location / Edit Saved Location | `SavedLocationsComponents.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | 26dp corners instead of standardized 24dp; non-standardized header wrapper | `AppFloatingBottomSheet` | Completed |
| Restore Preview (Loaded) | `SavedLocationsComponents.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Substantial selection workflow rendered in a floating dialog instead of bottom sheet | `AppFloatingBottomSheet` | Completed |
| Restore Confirmation (Empty / Failure) | `SavedLocationsComponents.kt` | `AppConfirmationDialog` | `surface` | 100% | 20dp all | None | None | None | **P1** | Raw M3 AlertDialog without standardized tokens | `AppConfirmationDialog` | Completed |
| Business Purposes List | `SettingsScreen.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Floating centered dialog with translucent background; does not pull to close | `AppFloatingBottomSheet` | Completed |
| Saved Locations List | `SettingsScreen.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Floating centered dialog with translucent background; does not pull to close | `AppFloatingBottomSheet` | Completed |
| Add Business Purpose | `SettingsScreen.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | 26dp corners; ad-hoc header layout | `AppFloatingBottomSheet` | Completed |
| Edit Business Purpose | `SettingsScreen.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Editing form rendered in an alert dialog rather than bottom sheet editor | `AppFloatingBottomSheet` | Completed |
| Delete Business Purpose | `SettingsScreen.kt` | `AppConfirmationDialog` | `surface` | 100% | 20dp all | None | None | None | **P1** | Confirmation alert lacks cohesive typography and button styling | `AppConfirmationDialog` | Completed |
| Delete Saved Location | `SettingsScreen.kt` | `AppConfirmationDialog` | `surface` | 100% | 20dp all | None | None | None | **P1** | Confirmation alert lacks cohesive typography and button styling | `AppConfirmationDialog` | Completed |
| Version History | `SettingsScreen.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Translucent surface (alpha 0.8), ad-hoc blur applied to background LazyColumn | `AppFloatingBottomSheet` | Completed |
| Work Schedule Time Picker | `WheelTimePickerDialog.kt` | Custom `Dialog` + `Box` | `surface` | 100% | 24dp all | Custom drag gesture | Manual drag threshold | None | **P1** | Re-implements vertical drag gestures manually on a dialog | Standardize or preserve with opaque surface & tokens | Standardized |
| Edit Trip | `TripDetailScreen.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Missing standardized drag handle, close button, and 24dp corner tokens | `AppFloatingBottomSheet` | Completed |
| Trip Delete Confirmation | `TripDetailScreen.kt` | `AppConfirmationDialog` | `surface` | 100% | 20dp all | None | None | None | **P1** | Ad-hoc alert dialog | `AppConfirmationDialog` | Completed |
| Trip Discard Confirmation | `TripDetailScreen.kt` | `AppConfirmationDialog` | `surface` | 100% | 20dp all | None | None | None | **P1** | Ad-hoc alert dialog | `AppConfirmationDialog` | Completed |
| Trip Classification (Swipe Card) | `SwipeableTripCard.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Lacks standard header, close button, and 24dp top corner radius | `AppFloatingBottomSheet` | Completed |
| History Filter & Sort Picker | `TripHistoryScreen.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Lacks standard drag handle, title header with close icon, and 24dp corners | `AppFloatingBottomSheet` | Completed |
| Create Report Dialog | `ReportsExportScreen.kt` | `AppFloatingBottomSheet` | `surface` | 100% | 24dp top | Centered pill (32x4) | Yes (M3) | IME + Nav | **P1** | Translucent background (alpha 0.8); substantial workflow in a dialog | `AppFloatingBottomSheet` | Completed |
| PDF Preview Dialog | `ReportsExportScreen.kt` | `Dialog` + `Surface` | `surface` | 100% | 20dp all | None | None | None | **P1** | Translucent container (alpha 0.95) with manual dimming | Opaque Dialog (100% surface) | Completed |
| Background Settings Dialog | `MainActivity.kt` | `AppConfirmationDialog` | `surface` | 100% | 20dp all | None | None | None | **P1** | Ad-hoc alert dialog | `AppConfirmationDialog` | Completed |
| Settings Floating Cards | `SettingsScreen.kt` | `Card` / `Surface` | `surface` | 100% | 20dp | N/A | N/A | N/A | **P2** | 22dp corners instead of standardized 20dp card token | `AppFloatingCard` / `AppShapes.FloatingCard` | Completed |
| Dashboard Floating Cards | `DashboardScreen.kt` | `Card` / `Surface` | `surface` | 100% | 16–24dp | N/A | N/A | N/A | **P2** | Inconsistent card corner radii (16dp, 18dp, 20dp, 24dp) | `AppFloatingCard` | Migrating |
| History Trip Cards | `TripHistoryScreen.kt` | `Card` | `surface` | 100% | 16dp | N/A | N/A | N/A | **P2** | Card radius 16dp vs 20dp standard | `AppFloatingCard` | Migrating |

---

## 4. Architectural Rules Established

1. **Type A: Floating Bottom Sheet (`AppFloatingBottomSheet`)**
   - Foundation: Material 3 `ModalBottomSheet`.
   - Visuals: Top corner radius `24.dp`, fully opaque surface, centered drag handle (`32.dp` x `4.dp`, radius `2.dp`, `#94A3B8`), standard header with `20.sp` semibold title and `48.dp` close touch target.
   - Behavior: Swipe-down dismissal, scrim tap dismissal, back button dismissal. IME and navigation bar inset aware.
   - Used for: All editing, creating, and multi-option selection workflows.

2. **Type B: Centered Confirmation Dialog (`AppConfirmationDialog`)**
   - Foundation: Material 3 `AlertDialog` / `Dialog`.
   - Visuals: Opaque surface on all four sides (`20.dp`), **no drag handle**, **no swipe-down**.
   - Behavior: Clear primary action and cancel button, standard scrim.
   - Used for: Destructive deletions, overwrite approvals, and critical warnings.

3. **Type C: Anchored Menu (`AppDropdownMenu`)**
   - Foundation: Material 3 `DropdownMenu`.
   - Visuals: Opaque surface, `12.dp` corner radius, `48.dp` minimum row touch target.
   - Used for: Contextual overflow actions (e.g. export options, quick vehicle selector).

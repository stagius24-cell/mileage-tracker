package com.businesstracker.mileage.domain.model

object TripPurpose {
    const val NURSERY_PLANT_PICKUP = "Nursery / Plant Pickup"
    const val PACKAGING_SUPPLIES = "Packaging Supplies"
    const val SHIPPING_ERRANDS = "Shipping Errands"
    const val WHOLESALE = "Wholesale"
    const val CLIENT_MEETING = "Client Meeting"
    const val GENERAL_BUSINESS = "General Business"
    const val PERSONAL = "Personal"

    val DEFAULT_SUCCULENT_PURPOSES = listOf(
        NURSERY_PLANT_PICKUP,
        PACKAGING_SUPPLIES,
        SHIPPING_ERRANDS,
        WHOLESALE
    )

    val DEFAULT_HANEELOVE_PURPOSES = listOf(
        PACKAGING_SUPPLIES,
        SHIPPING_ERRANDS,
        WHOLESALE,
        CLIENT_MEETING
    )
}

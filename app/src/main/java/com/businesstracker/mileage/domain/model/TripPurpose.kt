package com.businesstracker.mileage.domain.model

object TripPurpose {
    const val NURSERY_PICKUP = "Nursery Pickup"
    const val PLANT_DROP_DELIVERY = "Plant Drop / Delivery"
    const val WHOLESALE_SUPPLIES = "Wholesale Supplies"
    const val SOIL_POTS = "Soil & Pots Sourcing"
    const val SHIPPING_POST_OFFICE = "Shipping & Post Office"
    const val CLIENT_MEETING = "Client Meeting"
    const val GENERAL_BUSINESS = "General Business"
    const val PERSONAL = "Personal"

    val DEFAULT_SUCCULENT_PURPOSES = listOf(
        NURSERY_PICKUP,
        PLANT_DROP_DELIVERY,
        WHOLESALE_SUPPLIES,
        SOIL_POTS,
        SHIPPING_POST_OFFICE,
        CLIENT_MEETING,
        GENERAL_BUSINESS
    )
}

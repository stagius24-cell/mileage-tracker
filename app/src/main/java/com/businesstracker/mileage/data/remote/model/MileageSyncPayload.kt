package com.businesstracker.mileage.data.remote.model

import com.google.gson.annotations.SerializedName

data class MileageSyncPayload(
    @SerializedName("id")
    val id: String,

    @SerializedName("date")
    val date: String,

    @SerializedName("businessEntity")
    val businessEntity: String,

    @SerializedName("distanceMiles")
    val distanceMiles: Double,

    @SerializedName("purpose")
    val purpose: String,

    @SerializedName("startAddress")
    val startAddress: String?,

    @SerializedName("endAddress")
    val endAddress: String?,

    @SerializedName("rate")
    val rate: Double,

    @SerializedName("deduction")
    val deduction: Double
)

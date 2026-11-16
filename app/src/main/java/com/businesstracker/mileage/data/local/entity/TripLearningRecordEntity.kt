package com.businesstracker.mileage.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_learning_records",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RoutePatternEntity::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["patternId"])
    ]
)
data class TripLearningRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "tripId")
    val tripId: String,

    @ColumnInfo(name = "eligible")
    val eligible: Boolean,

    @ColumnInfo(name = "classificationSource")
    val classificationSource: String,

    @ColumnInfo(name = "confirmedCategory")
    val confirmedCategory: String?,

    @ColumnInfo(name = "businessEntity")
    val businessEntity: String?,

    @ColumnInfo(name = "purpose")
    val purpose: String?,

    @ColumnInfo(name = "startLatitude")
    val startLatitude: Double,

    @ColumnInfo(name = "startLongitude")
    val startLongitude: Double,

    @ColumnInfo(name = "endLatitude")
    val endLatitude: Double,

    @ColumnInfo(name = "endLongitude")
    val endLongitude: Double,

    @ColumnInfo(name = "patternId")
    val patternId: String? = null,

    @ColumnInfo(name = "learnedAt")
    val learnedAt: Long? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long
)

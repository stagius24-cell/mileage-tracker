package com.businesstracker.mileage.di

import android.content.Context
import androidx.room.Room
import com.businesstracker.mileage.data.local.ActiveTripDao
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.GpsInboxDao
import com.businesstracker.mileage.data.local.TripDao
import com.businesstracker.mileage.data.local.TripLifecycleDao
import com.businesstracker.mileage.data.local.TripSequenceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideExpenseTrackerDatabase(
        @ApplicationContext context: Context
    ): ExpenseTrackerDatabase {
        return Room.databaseBuilder(
            context,
            ExpenseTrackerDatabase::class.java,
            ExpenseTrackerDatabase.DATABASE_NAME
        )
        .addMigrations(
            ExpenseTrackerDatabase.MIGRATION_1_2,
            ExpenseTrackerDatabase.MIGRATION_2_3,
            ExpenseTrackerDatabase.MIGRATION_1_3,
            ExpenseTrackerDatabase.MIGRATION_3_4,
            ExpenseTrackerDatabase.MIGRATION_2_4,
            ExpenseTrackerDatabase.MIGRATION_1_4,
            ExpenseTrackerDatabase.MIGRATION_4_5,
            ExpenseTrackerDatabase.MIGRATION_3_5,
            ExpenseTrackerDatabase.MIGRATION_2_5,
            ExpenseTrackerDatabase.MIGRATION_1_5
        )
        .build()
    }

    @Provides
    @Singleton
    fun provideTripDao(database: ExpenseTrackerDatabase): TripDao {
        return database.tripDao()
    }

    @Provides
    @Singleton
    fun provideActiveTripDao(database: ExpenseTrackerDatabase): ActiveTripDao {
        return database.activeTripDao()
    }

    @Provides
    @Singleton
    fun provideGpsInboxDao(database: ExpenseTrackerDatabase): GpsInboxDao {
        return database.gpsInboxDao()
    }

    @Provides
    @Singleton
    fun provideTripLifecycleDao(database: ExpenseTrackerDatabase): TripLifecycleDao {
        return database.tripLifecycleDao()
    }

    @Provides
    @Singleton
    fun provideTripSequenceDao(database: ExpenseTrackerDatabase): TripSequenceDao {
        return database.tripSequenceDao()
    }
}

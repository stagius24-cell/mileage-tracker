package com.businesstracker.mileage.di

import android.content.Context
import androidx.room.Room
import com.businesstracker.mileage.data.local.ActiveTripDao
import com.businesstracker.mileage.data.local.ExpenseTrackerDatabase
import com.businesstracker.mileage.data.local.TripDao
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
        .fallbackToDestructiveMigration()
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
}

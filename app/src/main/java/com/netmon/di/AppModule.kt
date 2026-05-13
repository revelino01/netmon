package com.netmon.di

import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import com.netmon.data.AppDatabase
import com.netmon.data.TrafficLogDao
import com.netmon.data.DnsQueryLogDao
import com.netmon.domain.TrafficRepository
import com.netmon.data.TrafficRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePackageManager(@ApplicationContext context: Context): PackageManager =
        context.packageManager

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "netmon-db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTrafficLogDao(db: AppDatabase): TrafficLogDao = db.trafficLogDao()

    @Provides
    fun provideDnsQueryLogDao(db: AppDatabase): DnsQueryLogDao = db.dnsQueryLogDao()

    @Provides
    @Singleton
    fun provideTrafficRepository(impl: TrafficRepositoryImpl): TrafficRepository = impl
}
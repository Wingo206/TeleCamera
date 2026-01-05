package com.example.telecamera.di

import android.content.Context
import com.example.telecamera.data.camera.CameraXManager
import com.example.telecamera.data.connection.MessageSerializer
import com.example.telecamera.data.connection.NearbyConnectionManager
import com.example.telecamera.data.connection.NearbyPairingStrategy
import com.example.telecamera.data.feedback.FeedbackManager
import com.example.telecamera.domain.connection.IPairingStrategy
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
    fun provideCameraXManager(
        @ApplicationContext context: Context
    ): CameraXManager {
        return CameraXManager(context)
    }

    @Provides
    @Singleton
    fun provideMessageSerializer(): MessageSerializer {
        return MessageSerializer()
    }

    @Provides
    @Singleton
    fun providePairingStrategy(
        @ApplicationContext context: Context
    ): NearbyPairingStrategy {
        return NearbyPairingStrategy(context)
    }

    @Provides
    @Singleton
    fun provideIPairingStrategy(
        nearbyPairingStrategy: NearbyPairingStrategy
    ): IPairingStrategy {
        return nearbyPairingStrategy
    }

    @Provides
    @Singleton
    fun provideConnectionManager(
        @ApplicationContext context: Context,
        pairingStrategy: IPairingStrategy,
        messageSerializer: MessageSerializer
    ): NearbyConnectionManager {
        return NearbyConnectionManager(context, pairingStrategy, messageSerializer)
    }

    @Provides
    @Singleton
    fun provideFeedbackManager(
        @ApplicationContext context: Context
    ): FeedbackManager {
        return FeedbackManager(context)
    }
}

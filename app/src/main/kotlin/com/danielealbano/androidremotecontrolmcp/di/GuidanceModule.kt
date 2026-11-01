package com.danielealbano.androidremotecontrolmcp.di

import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GuidanceModule {
    @Binds
    @Singleton
    abstract fun bindGuidanceController(impl: GuidanceControllerImpl): GuidanceController
}

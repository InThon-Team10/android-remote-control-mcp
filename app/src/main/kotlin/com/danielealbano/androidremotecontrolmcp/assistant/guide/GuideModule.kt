package com.danielealbano.androidremotecontrolmcp.assistant.guide

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GuideModule {
    @Binds
    @Singleton
    abstract fun bindGuideSteps(impl: GuideStepsImpl): GuideSteps
}

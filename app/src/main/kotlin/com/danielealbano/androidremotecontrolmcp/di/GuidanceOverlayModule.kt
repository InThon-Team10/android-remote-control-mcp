package com.danielealbano.androidremotecontrolmcp.di

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import com.danielealbano.androidremotecontrolmcp.ui.helper.GuidanceOverlayHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class GuidanceOverlayModule {
    @Binds
    @IntoSet
    abstract fun bindGuidanceOverlayHost(impl: GuidanceOverlayHost): AccessibilityOverlayHost
}

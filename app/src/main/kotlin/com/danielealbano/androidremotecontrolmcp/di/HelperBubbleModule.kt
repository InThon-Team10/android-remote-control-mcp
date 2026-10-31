package com.danielealbano.androidremotecontrolmcp.di

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import com.danielealbano.androidremotecontrolmcp.ui.helper.bubble.HelperBubbleOverlayHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class HelperBubbleModule {
    @Binds
    @IntoSet
    abstract fun bindBubbleHost(impl: HelperBubbleOverlayHost): AccessibilityOverlayHost
}

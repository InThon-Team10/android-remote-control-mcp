package com.danielealbano.androidremotecontrolmcp.di

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperBubble
import com.danielealbano.androidremotecontrolmcp.ui.helper.bubble.HelperBubbleOverlayHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HelperOverlayModule {
    /** Overlay hosts attached by McpAccessibilityService; each contributes itself with `@Binds @IntoSet`. */
    @Multibinds
    abstract fun accessibilityOverlayHosts(): Set<AccessibilityOverlayHost>

    @Binds
    @Singleton
    abstract fun bindHelperBubble(impl: HelperBubbleOverlayHost): HelperBubble
}

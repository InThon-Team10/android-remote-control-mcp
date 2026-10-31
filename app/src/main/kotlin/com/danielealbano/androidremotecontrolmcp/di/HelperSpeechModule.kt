package com.danielealbano.androidremotecontrolmcp.di

import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperSpeaker
import com.danielealbano.androidremotecontrolmcp.ui.helper.TextToSpeechHelperSpeaker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HelperSpeechModule {
    @Binds
    @Singleton
    abstract fun bindHelperSpeaker(impl: TextToSpeechHelperSpeaker): HelperSpeaker

    /** The engine follows the accessibility service lifecycle, so it is attached like an overlay host. */
    @Binds
    @IntoSet
    abstract fun bindSpeakerHost(impl: TextToSpeechHelperSpeaker): AccessibilityOverlayHost
}

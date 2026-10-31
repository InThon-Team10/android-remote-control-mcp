package com.danielealbano.androidremotecontrolmcp.assistant.openai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt bindings of the OpenAI SDK seams shared by the assistant's text agent and live voice session. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OpenAiModule {
    @Binds
    @Singleton
    abstract fun bindOpenAiClientProvider(impl: OpenAiSdkClientProvider): OpenAiClientProvider

    @Binds
    @Singleton
    abstract fun bindOpenAiResponsesGateway(impl: OpenAiSdkResponsesGateway): OpenAiResponsesGateway
}

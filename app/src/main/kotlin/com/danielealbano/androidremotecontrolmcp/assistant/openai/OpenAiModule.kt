package com.danielealbano.androidremotecontrolmcp.assistant.openai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt binding of the OpenAI SDK client shared by the assistant's planner and live voice session. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OpenAiModule {
    @Binds
    @Singleton
    abstract fun bindOpenAiClientProvider(impl: OpenAiSdkClientProvider): OpenAiClientProvider
}

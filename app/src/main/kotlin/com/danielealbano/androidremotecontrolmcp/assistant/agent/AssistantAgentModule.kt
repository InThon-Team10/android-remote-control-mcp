package com.danielealbano.androidremotecontrolmcp.assistant.agent

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt bindings of the assistant agent (separate from `RepositoryModule` to stay under the function limit). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantAgentModule {
    @Binds
    @Singleton
    abstract fun bindAssistantAgent(impl: GeminiAssistantAgent): AssistantAgent

    @Binds
    @Singleton
    abstract fun bindGeminiModelGateway(impl: GeminiSdkModelGateway): GeminiModelGateway
}

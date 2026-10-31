package com.danielealbano.androidremotecontrolmcp.data.security

import com.danielealbano.androidremotecontrolmcp.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/** The app's OpenAI key embedded at build time, used while the user has not saved a key of their own. */
fun interface BuiltInApiKeyProvider {
    /** The embedded key, or null when the build has none. */
    fun builtInKey(): String?
}

@Singleton
class BuildConfigBuiltInApiKeyProvider
    @Inject
    constructor() : BuiltInApiKeyProvider {
        override fun builtInKey(): String? = BuildConfig.OPENAI_API_KEY.takeIf { it.isNotBlank() }
    }

package app.samloader.common.network

import io.ktor.client.engine.*

// Provides platform-specific Ktor engine in a multiplatform-safe way
expect fun provideEngine(): HttpClientEngineFactory<*>

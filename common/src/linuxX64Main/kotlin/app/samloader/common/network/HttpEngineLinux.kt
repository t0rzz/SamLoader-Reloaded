package app.samloader.common.network

import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*

actual fun provideEngine(): HttpClientEngineFactory<*> = Curl

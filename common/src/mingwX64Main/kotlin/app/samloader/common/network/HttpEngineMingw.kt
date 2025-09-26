package app.samloader.common.network

import io.ktor.client.engine.*
import io.ktor.client.engine.winhttp.*

actual fun provideEngine(): HttpClientEngineFactory<*> = WinHttp

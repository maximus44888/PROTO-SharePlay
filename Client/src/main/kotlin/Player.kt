package tfg.proto.shareplay

import kotlinx.coroutines.flow.SharedFlow

interface Player {
    suspend fun loadFile(fileIdentifier: String)

    suspend fun pause(pause: Boolean)

    suspend fun jumpTo(time: Double)

    suspend fun observePause(): SharedFlow<Boolean>

    suspend fun observeSeek(): SharedFlow<Double>
}

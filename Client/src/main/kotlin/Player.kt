package tfg.proto.shareplay

import kotlinx.coroutines.flow.SharedFlow

interface Player {
    suspend fun loadFile(fileIdentifier: String)

    suspend fun pause(pause: Boolean)

    suspend fun seek(time: Double)

    val pauseEvents: SharedFlow<Boolean>

    val seekEvents: SharedFlow<Double>
}

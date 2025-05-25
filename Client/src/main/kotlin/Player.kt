package tfg.proto.shareplay

import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration

interface Player {
    suspend fun loadFile(fileIdentifier: String)

    suspend fun resume()

    suspend fun pause()

    suspend fun seek(time: Duration)

    val pauseEvents: SharedFlow<Boolean>

    val seekEvents: SharedFlow<Duration>
}

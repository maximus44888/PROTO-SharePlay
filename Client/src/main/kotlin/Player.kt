package tfg.proto.shareplay

import java.net.URI
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration
import kotlin.time.DurationUnit

interface Player {
    val durationUnit: DurationUnit

    suspend fun loadMedia(mediaURI: URI)

    suspend fun resume()

    suspend fun pause()

    suspend fun seek(time: Duration)

    val loadedMediaEvents: SharedFlow<URI>

    val pauseEvents: SharedFlow<Boolean>

    val seekEvents: SharedFlow<Duration>
}

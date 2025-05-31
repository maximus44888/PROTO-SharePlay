package tfg.proto.shareplay

import java.net.URI
import kotlinx.coroutines.flow.SharedFlow
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.DurationUnit

interface Player {

    companion object {
        fun buildURI(mediaPath: String) = runCatching { Path(mediaPath).toUri() }
            .getOrElse {
                runCatching { URI(mediaPath) }.getOrNull()
            }
    }

    val durationUnit: DurationUnit

    suspend fun loadMedia(mediaURI: URI)

    suspend fun resume()

    suspend fun pause()

    suspend fun seek(time: Duration)

    val loadedMediaEvents: SharedFlow<URI>

    val pauseEvents: SharedFlow<Boolean>

    val seekEvents: SharedFlow<Duration>
}

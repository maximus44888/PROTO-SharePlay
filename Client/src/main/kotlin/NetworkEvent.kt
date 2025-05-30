package tfg.proto.shareplay

import java.io.Serializable
import java.net.URI
import kotlin.time.Duration

sealed class NetworkEvent : Serializable {
    data class MediaLoaded(val mediaURI: URI) : NetworkEvent()
    data class Pause(val paused: Boolean) : NetworkEvent()
    data class Seek(val time: Duration) : NetworkEvent()
}

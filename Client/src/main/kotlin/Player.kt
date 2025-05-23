package tfg.proto.shareplay

import kotlinx.coroutines.channels.ReceiveChannel

interface Player {
    suspend fun loadFile(fileIdentifier: String)

    suspend fun pause(pause: Boolean)

    suspend fun getPause(): Boolean?

    suspend fun jumpTo(time: Int)

    suspend fun getPlaybackTime(): Double?

    suspend fun observePause(): ReceiveChannel<Boolean>

    suspend fun observeSeek(): ReceiveChannel<Double>
}

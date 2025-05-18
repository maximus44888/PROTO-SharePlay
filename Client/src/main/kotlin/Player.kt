package tfg.proto.shareplay

import kotlinx.coroutines.channels.ReceiveChannel

interface Player {
    suspend fun loadFile(fileIdentifier: String)

    suspend fun pause()
    suspend fun play()

    suspend fun jumpTo(time: Int)

    suspend fun observePause(): ReceiveChannel<Boolean>
}

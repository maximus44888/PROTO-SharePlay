package tfg.proto.shareplay

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.coroutines.cancellation.CancellationException

class PlayerClient(
    private val socket: Socket,
    private val roomName: String,
    private val nickName: String,
    private val player: Player,
) : AutoCloseable {
    val _clients = mutableListOf<String>()
    val clients get() = _clients.toList()
    private val output = ObjectOutputStream(socket.getOutputStream())
    private val input = ObjectInputStream(socket.getInputStream())
    private val playerScope = CoroutineScope(Dispatchers.IO) + SupervisorJob()

    init {
        try {
            output.writeUTF(nickName)
            output.flush()

            output.writeUTF(roomName)
            output.flush()

            playerScope.launch {
                launch(Dispatchers.IO) {
                    player.pauseEvents.collect {
                        output.writeObject(NetworkEvent.Pause(it))
                        output.flush()
                    }
                }
                launch(Dispatchers.IO) {
                    player.seekEvents.collect {
                        output.writeObject(NetworkEvent.Seek(it))
                        output.flush()
                    }
                }

                launch(Dispatchers.IO) {
                    while (true) {
                        input.readObject().let { event ->
                            when (event) {
                                is NetworkEvent.Pause -> if (event.paused) player.pause() else player.resume()
                                is NetworkEvent.Seek -> player.seek(event.time)
                                is NetworkEvent.NewClient -> _clients.add(event.nickName)
                                is NetworkEvent.ClientLeft -> _clients.remove(event.nickName)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("ERROR IN PLAYER CLIENT: ${e.message}")
            close()
            throw e
        }
    }

    fun loadMedia(mediaPath: String) {
        val mediaURI = Player.buildURI(mediaPath) ?: throw IllegalArgumentException("Invalid media path: $mediaPath")
        playerScope.launch {
            player.loadMedia(mediaURI)
            output.writeObject(NetworkEvent.MediaLoaded(mediaURI))
            output.flush()
        }
    }

    override fun close() {
        playerScope.cancel(CancellationException("PlayerClient closed"))
        player.close()
        output.close()
        input.close()
        socket.close()
    }
}

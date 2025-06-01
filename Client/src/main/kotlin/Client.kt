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
                    player.loadedMediaEvents.collect {
                        output.writeObject(NetworkEvent.MediaLoaded(it))
                        output.flush()
                    }
                }
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
                                is NetworkEvent.MediaLoaded -> player.loadMedia(event.mediaURI)
                                is NetworkEvent.Pause -> if (event.paused) player.pause() else player.resume()
                                is NetworkEvent.Seek -> player.seek(event.time)
                                is NetworkEvent.NewClient -> println("${event.nickName} joined the room")
                                is NetworkEvent.ClientLeft -> println("${event.nickName} left the room")
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

    override fun close() {
        playerScope.cancel(CancellationException("PlayerClient closed"))
        player.close()
        output.close()
        input.close()
        socket.close()
    }
}

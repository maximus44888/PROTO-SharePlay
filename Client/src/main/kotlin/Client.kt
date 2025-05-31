package tfg.proto.shareplay

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PlayerClient(
    socket: Socket,
    private val roomName: String,
    private val player: Player,
) {
    init {
        socket.withObjectStreams { output, input ->
            output.writeUTF(roomName)
            output.flush()

            coroutineScope {
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
                            }
                        }
                    }
                }
            }
        }
    }

    fun Socket.withObjectStreams(block: suspend (ObjectOutputStream, ObjectInputStream) -> Unit) {
        ObjectOutputStream(getOutputStream()).use { output ->
            ObjectInputStream(getInputStream()).use { input ->
                CoroutineScope(Dispatchers.IO).launch { block(output, input) }
            }
        }
    }

}

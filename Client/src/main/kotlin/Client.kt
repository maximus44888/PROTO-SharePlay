package tfg.proto.shareplay

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val mpvPath = "mpv"

suspend fun main() {
    val player = MPV(mpvPath)
    Socket("localhost", 1234).withObjectStreams { output, input ->
        print("Insert room name: ")
        output.writeUTF(readln())
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

suspend fun Socket.withObjectStreams(block: suspend (ObjectOutputStream, ObjectInputStream) -> Unit) {
    ObjectOutputStream(getOutputStream()).use { output ->
        ObjectInputStream(getInputStream()).use { input ->
            block(output, input)
        }
    }
}

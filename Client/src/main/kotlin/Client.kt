package tfg.proto.shareplay

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val mediaURL =
    """https://youtu.be/dQw4w9WgXcQ?si=HD7Uz2iicApUBFUf"""
const val mediaPath =
    """C:\Users\jmaxi\Mis ficheros\Anime\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\S01E01-Undertaker [2F703024].mkv"""
const val mpvPath = "mpv"

fun main() {
    val player = MPV(mpvPath)
    Socket("localhost", 1234).streams { input, output ->

        print("Insert room name:")
        output.writeUTF(readln())

        coroutineScope {
            launch {
                player.loadedMediaEvents.collect {
                    output.writeObject(NetworkEvent.MediaLoaded(it))
                }
            }
            launch {
                player.pauseEvents.collect {
                    output.writeObject(NetworkEvent.Pause(it))
                }
            }
            launch {
                player.seekEvents.collect {
                    output.writeObject(NetworkEvent.Seek(it))
                }
            }

            launch {
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

fun Socket.streams(action: suspend (ObjectInputStream, ObjectOutputStream) -> Unit) {
    val input = this.getInputStream()
    val output = this.getOutputStream()
    val dataInput = ObjectInputStream(input)
    val dataOutput = ObjectOutputStream(output)
    CoroutineScope(Dispatchers.IO).launch { action(dataInput, dataOutput) }
    dataInput.close()
    dataOutput.close()
}

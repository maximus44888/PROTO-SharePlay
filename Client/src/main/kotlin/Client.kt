package tfg.proto.shareplay

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val mediaPath =
    """C:\Users\jmaxi\Mis ficheros\Anime\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\S01E01-Undertaker [2F703024].mkv"""

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun main() {
    Socket(InetAddress.getByName("localhost"), 1234).use { socket ->
        println("Connected to server at ${socket.inetAddress.hostAddress}:${socket.localPort}")

        DataInputStream(socket.inputStream).use { objectInputStream ->
            DataOutputStream(socket.outputStream).use { objectOutputStream ->
                print("Enter room name: ")
                objectOutputStream.writeUTF(readln())
                coroutineScope {
                    val requests: ReceiveChannel<IPC.Request> = produce {
                        launch {
                            send(IPC.Request.LoadFile(mediaPath))
                            send(IPC.Request.SetProperty(IPC.Property.VOLUME, 0))
                            send(IPC.Request.SetProperty(IPC.Property.PAUSE, true))

                            val randomPlaybackTime = (0..666).random()
                            println("Random playback time: $randomPlaybackTime")

                            while (true) {
                                withContext(Dispatchers.IO) { readln() }
                                objectOutputStream.writeInt(randomPlaybackTime)
                                send(IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, randomPlaybackTime))
                            }
                        }

                        launch {
                            while (true) {
                                val requestedPlaybackTime = withContext(Dispatchers.IO) { objectInputStream.readInt() }
                                send(IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, requestedPlaybackTime))
                            }
                        }
                    }

                    MPV(requests, this)
                }
            }
        }
    }
}

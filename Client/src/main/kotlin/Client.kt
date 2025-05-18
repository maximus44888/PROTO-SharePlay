package tfg.proto.shareplay

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val mediaPath =
    """C:\Users\jmaxi\Mis ficheros\Anime\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\S01E01-Undertaker [2F703024].mkv"""
const val mpvPath = "mpv"

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun main() {
    Socket(InetAddress.getByName("localhost"), 1234).use { socket ->
        println("Connected to server at ${socket.inetAddress.hostAddress}:${socket.localPort}")

        DataInputStream(socket.inputStream).use { objectInputStream ->
            DataOutputStream(socket.outputStream).use { objectOutputStream ->
                print("Enter room name: ")
                objectOutputStream.writeUTF(readln())
                coroutineScope {
                    val mpv = MPV(mpvPath)

                    launch(Dispatchers.IO) {
                        while (true) {
                            readln()
                            mpv.pause()
                            objectOutputStream.writeBoolean(true)
                        }
                    }

                    launch(Dispatchers.IO) {
                        while (true) {
                            if (objectInputStream.readBoolean()) {
                                mpv.pause()
                            }
                        }
                    }
                }
            }
        }
    }
}

package tfg.proto.shareplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.Socket

fun main() {
    Socket(InetAddress.getByName("localhost"), 1235).use { socket ->
        println("Connected to server at ${socket.inetAddress.hostAddress}:${socket.localPort}")

        DataInputStream(socket.inputStream).use { objectInputStream ->
            DataOutputStream(socket.outputStream).use { objectOutputStream ->
                val roomId = readln().toInt()
                objectOutputStream.writeInt(roomId)
                println("Asked to join room $roomId")

                runBlocking {
                    launch {
                        while (true) {
                            val message = withContext(Dispatchers.IO) {
                                objectInputStream.readUTF()
                            }
                            println("Received message: $message")
                        }
                    }

                    launch {
                        while (true) {
                            val message = withContext(Dispatchers.IO) {
                                readln()
                            }
                            objectOutputStream.writeUTF(message)
                        }
                    }
                }
            }
        }
    }
}

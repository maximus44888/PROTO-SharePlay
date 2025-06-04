package tfg.proto.shareplay

import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class Room {
    private val clients = ConcurrentLinkedQueue<Client>()

    suspend fun handleNewClient(client: Client) = supervisorScope {
        try {
            clients.add(client)

            clients.filterNot { it == client }.forEach {
                it.sendObject(NetworkEvent.NewClient(client.nickName))
                client.sendObject(NetworkEvent.NewClient(it.nickName))
            }

            while (true) {
                val networkEvent = client.readObject()

                clients.filterNot { it == client }.forEach {
                    it.sendObject(networkEvent)
                }
            }
        } catch (e: IOException) {
            println("Client disconnected: ${client.nickName} - ${e.message}")
        } catch (e: Exception) {
            println("Error handling client ${client.nickName}: ${e.message}")
        } finally {
            handleClientDisconnect(client)
        }
    }

    private suspend fun handleClientDisconnect(client: Client) {
        try {
            clients.remove(client)
            clients.forEach { it.sendObject(NetworkEvent.ClientLeft(client.nickName)) }
        } catch (e: Exception) {
            println("Error during client disconnect cleanup: ${e.message}")
        } finally {
            client.close()
        }
    }

    class Client(
        private val socket: Socket
    ) {
        private val objectInputStream = ObjectInputStream(socket.inputStream)
        private val objectOutputStream = ObjectOutputStream(socket.outputStream)

        val nickName: String = objectInputStream.readUTF()

        suspend fun readObject(): Any = withContext(Dispatchers.IO) { objectInputStream.readObject() }

        internal suspend fun sendObject(message: Any) = withContext(Dispatchers.IO) {
            objectOutputStream.writeObject(message)
            objectOutputStream.flush()
        }

        internal suspend fun readUTF() = withContext(Dispatchers.IO) { objectInputStream.readUTF() }

        internal suspend fun close() = withContext(Dispatchers.IO) {
            try {
                objectInputStream.close()
            } catch (e: Exception) {
                println("Error closing input stream: ${e.message}")
            }

            try {
                objectOutputStream.close()
            } catch (e: Exception) {
                println("Error closing output stream: ${e.message}")
            }

            try {
                socket.close()
            } catch (e: Exception) {
                println("Error closing socket: ${e.message}")
            }
        }

        override fun toString() = "${socket.inetAddress.hostAddress}:${socket.port}"
    }

    companion object {
        private val rooms = ConcurrentHashMap<String, Room>()

        suspend fun handleNewClient(client: Client) = supervisorScope {
            try {
                val roomName = client.readUTF()
                rooms.computeIfAbsent(roomName) { Room() }.handleNewClient(client)
            } catch (e: Exception) {
                println("Error handling new client connection: ${e.message}")
            } finally {
                client.close()
            }
        }
    }
}

package tfg.proto.shareplay

import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class Room {
    private val clients = ConcurrentLinkedQueue<Client>()

    suspend fun handleNewClient(client: Client) {
        clients.add(client)

        clients.filterNot { it == client }.forEach {
            it.sendObject(NetworkEvent.NewClient(client.nickName))
            client.sendObject(NetworkEvent.NewClient(it.nickName))
        }

        try {
            while (true) {
                val networkEvent = client.readObject()

                clients.filterNot { it == client }.forEach {
                    it.sendObject(networkEvent)
                }
            }
        } catch (_: IOException) {
            clients.remove(client)
            clients.forEach { it.sendObject(NetworkEvent.ClientLeft(client.nickName)) }
        }
    }

    class Client(
        private val socket: Socket
    ) {
        private val objectInputStream = ObjectInputStream(socket.inputStream)
        private val objectOutputStream = ObjectOutputStream(socket.outputStream)

        val nickName: String = objectInputStream.readUTF()

        suspend fun readObject(): Any = withContext(Dispatchers.IO) { objectInputStream.readObject() }

        internal fun sendObject(message: Any) {
            objectOutputStream.writeObject(message)
            objectOutputStream.flush()
        }

        internal suspend fun readUTF() = withContext(Dispatchers.IO) { objectInputStream.readUTF() }

        override fun toString() = "${socket.inetAddress.hostAddress}:${socket.port}"
    }

    companion object {
        private val rooms = ConcurrentHashMap<String, Room>()

        suspend fun handleNewClient(socket: Client) {
            val roomName = socket.readUTF()

            rooms.computeIfAbsent(roomName) { Room() }.handleNewClient(socket)
        }

    }
}

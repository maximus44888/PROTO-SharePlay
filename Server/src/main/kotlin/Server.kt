package tfg.proto.shareplay

import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val rooms = Rooms()

suspend fun main() {
    ServerSocket(1234, 50, InetAddress.getByName("localhost")).use { serverSocket ->
        println("Server started at ${serverSocket.inetAddress.hostAddress}:${serverSocket.localPort}")
        while (!serverSocket.isClosed) {
            println("Waiting for new client...")
            val socket = withContext(Dispatchers.IO) { serverSocket.accept() }
            println("New client connected: ${socket.inetAddress.hostAddress}:${socket.port}")
            CoroutineScope(Dispatchers.IO).launch { rooms.handleNewClient(Room.Client(socket)) }
        }
        println("Closing server socket...")
    }
    println("Server stopped")
}

class Room(private val name: String) {
    private val clients = ConcurrentLinkedQueue<Client>()

    init {
        log("Room created")
    }

    suspend fun handleNewClient(client: Client) {
        clients.add(client)

        log("New client $client added. Total room clients: ${clients.size}")

        clients.filterNot { it == client }.forEach {
            it.sendObject(client.nickName)
            client.sendObject(it.nickName)
        }

        try {
            while (true) {
                val networkEvent = client.readObject()

                log("Client $client sent: $networkEvent")

                clients.filterNot { it == client }.forEach {
                    it.sendObject(networkEvent)
                }
            }
        } catch (e: IOException) {
            log("Client $client disconnected")
            clients.remove(client)
            log("Total room clients: ${clients.size}")
        }
    }

    fun log(message: String) = println("[$name] $message")

    class Client(
        private val socket: Socket
    ) {
        val nickName: String
        private val objectInputStream = ObjectInputStream(socket.inputStream)
        private val objectOutputStream = ObjectOutputStream(socket.outputStream)

        init {
            nickName = objectInputStream.readUTF()
        }

        suspend fun readObject(): Any = withContext(Dispatchers.IO) { objectInputStream.readObject() }

        internal fun sendObject(message: Any) {
            objectOutputStream.writeObject(message)
            objectOutputStream.flush()
        }

        internal suspend fun readUTF() = withContext(Dispatchers.IO) { objectInputStream.readUTF() }

        override fun toString() = "${socket.inetAddress.hostAddress}:${socket.port}"
    }
}

class Rooms {
    private val rooms = ConcurrentHashMap<String, Room>()

    private fun createRoomIfAbsent(name: String) = rooms.computeIfAbsent(name) { Room(name) }

    suspend fun handleNewClient(socket: Room.Client) {
        val roomName = socket.readUTF()

        println("Client $socket requested room: $roomName")

        createRoomIfAbsent(roomName).apply { handleNewClient(socket) }
    }
}

package tfg.proto.shareplay

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val rooms = Rooms()

suspend fun main() = coroutineScope {
    val newClients = newClientsProducer()

    newClients.consumeEach { newClient ->
        launch {
            rooms.handleNewClient(newClient)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.newClientsProducer() = produce {
    ServerSocket(1234, 50, InetAddress.getByName("localhost")).use { serverSocket ->
        println("Server started at ${serverSocket.inetAddress.hostAddress}:${serverSocket.localPort}")
        while (!serverSocket.isClosed) {
            println("Waiting for new client...")
            val socket = withContext(Dispatchers.IO) {
                serverSocket.accept()
            }
            println("New client connected: ${socket.inetAddress.hostAddress}:${socket.port}")
            send(Room.Client(socket))
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

        while (true) {
            val networkEvent = client.readObject()

            log("Client $client sent: $networkEvent")

            clients.filterNot { it == client }.forEach {
                it.sendObject(networkEvent)
            }
        }
    }

    fun log(message: String) {
        println("[$name] $message")
    }

    class Client(
        private val socket: Socket
    ) {
        private val objectInputStream = ObjectInputStream(socket.inputStream)
        private val objectOutputStream = ObjectOutputStream(socket.outputStream)

        suspend fun readObject(): Any = withContext(Dispatchers.IO) { objectInputStream.readObject() }

        internal fun sendObject(message: Any) = objectOutputStream.writeObject(message)

        internal suspend fun readUTF() = withContext(Dispatchers.IO) { objectInputStream.readUTF() }

        override fun toString() = "${socket.inetAddress.hostAddress}:${socket.port}"
    }
}

class Rooms {
    private val rooms = ConcurrentHashMap<String, Room>()

    fun createRoomIfAbsent(name: String) = rooms.computeIfAbsent(name) { Room(name) }

    suspend fun handleNewClient(client: Room.Client) {
        val roomName = client.readUTF()

        createRoomIfAbsent(roomName).apply {
            handleNewClient(client)
        }
    }
}

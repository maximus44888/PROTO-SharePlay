package tfg.proto.shareplay

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

val rooms = Rooms()

fun main() = runBlocking {
    val roomClients = roomClientProducer(clientSocketProducer())

    for (roomClient in roomClients) {
        launch {
            val roomId = roomClient.getRoomId()

            rooms.addIfAbsent(roomId).apply {
                handleNewClient(roomClient)
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.clientSocketProducer() = produce {
    ServerSocket(1234, 50, InetAddress.getByName("localhost")).use { serverSocket ->
        println("Server started.")
        while (!serverSocket.isClosed) {
            println("Waiting for client connection...")
            val socket = withContext(Dispatchers.IO) {
                serverSocket.accept()
            }
            println("Client connected.")
            send(socket)
        }
    }
    println("Server stopped.")
}

@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.roomClientProducer(clientsSockets: ReceiveChannel<Socket>) = produce {
    for (clientSocket in clientsSockets) send(Room.Client(clientSocket))
}

class Room(private val id: Int) {
    private val clients = ConcurrentLinkedQueue<Client>()

    suspend fun handleNewClient(client: Client) {
        clients.add(client)

        println("Client $client added to room $id. Total clients: ${clients.size}")

        //Notify the new client about the existing clients
        clients.forEach {
            if (it == client) return@forEach
            client.sendMessage("Existing client: $it")
        }

        // Notify all clients in the room about the new client
        clients.forEach {
            if (it == client) return@forEach
            it.sendMessage("New client joined: $client")
        }

        // Read messages from the client
        while (true) {
            val message = client.readMessage()
            println("Received message from $client: $message")

            // Broadcast the message to all clients in the room
            clients.forEach {
                if (it == client) return@forEach
                it.sendMessage("Message from $client: $message")
            }
        }
    }

    class Client(
        private val socket: Socket
    ) {
        private val dataInputStream: DataInputStream = DataInputStream(socket.inputStream)
        private val dataOutputStream: DataOutputStream = DataOutputStream(socket.outputStream)

        suspend fun getRoomId(): Int {
            return withContext(Dispatchers.IO) { dataInputStream.readInt() }
        }

        internal suspend fun readMessage(): String {
            return withContext(Dispatchers.IO) { dataInputStream.readUTF() }
        }

        internal fun sendMessage(message: String) {
            dataOutputStream.writeUTF(message)
        }

        override fun toString(): String {
            return "${socket.inetAddress.hostAddress}:${socket.port}"
        }
    }
}

class Rooms {
    private val rooms = ConcurrentHashMap<Int, Room>()

    fun addIfAbsent(id: Int): Room {
        return rooms.computeIfAbsent(id) { Room(id) }
    }
}
package tfg.proto.shareplay

import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun main() {
    ServerSocket(1234, 50, InetAddress.getByName("localhost")).use { serverSocket ->
        while (!serverSocket.isClosed) {
            val socket = withContext(Dispatchers.IO) { serverSocket.accept() }
            CoroutineScope(Dispatchers.IO).launch { Room.handleNewClient(Room.Client(socket)) }
        }
    }
}

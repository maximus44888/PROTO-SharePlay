package tfg.proto.shareplay

import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun main(vararg args: String) {
    val bindAddress = args.getOrNull(0) ?: "0.0.0.0"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 1234

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    ServerSocket(port, 50, InetAddress.getByName(bindAddress)).use { serverSocket ->
        println("Server started on $bindAddress:$port")

        while (!serverSocket.isClosed) {
            val socket = withContext(Dispatchers.IO) { serverSocket.accept() }
            scope.launch { Room.handleNewClient(Room.Client(socket)) }
        }
    }
}

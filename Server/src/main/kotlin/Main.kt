package tfg.proto.shareplay

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun main(vararg args: String) = coroutineScope {
    val bindAddress = args.getOrNull(0) ?: "0.0.0.0"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 1234

    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("Uncaught exception in client handler: ${exception.message}")
    }

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    try {
        ServerSocket(port, 50, InetAddress.getByName(bindAddress)).use { serverSocket ->
            println("Server started on $bindAddress:$port")

            while (!serverSocket.isClosed) {
                try {
                    val socket = withContext(Dispatchers.IO) { serverSocket.accept() }
                    println("New connection from ${socket.inetAddress.hostAddress}:${socket.port}")

                    scope.launch {
                        try {
                            Room.handleNewClient(Room.Client(socket))
                        } catch (e: Exception) {
                            println("Error handling client: ${e.message}")
                        }
                    }
                } catch (e: SocketException) {
                    if (serverSocket.isClosed) {
                        println("Server socket closed")
                        break
                    } else {
                        println("Socket error: ${e.message}")
                    }
                } catch (e: Exception) {
                    println("Error accepting connection: ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        println("Fatal server error: ${e.message}")
    }

    println("Server shutdown complete")
}

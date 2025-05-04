package tfg.proto.shareplay

import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.scalasbt.ipcsocket.Win32NamedPipeSocket
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
    val mpv = MPV()

    mpv.start()
}

class MPV {
    private val process: Process
    private val writer: PrintWriter
    private val reader: BufferedReader

    init {
        @OptIn(ExperimentalUuidApi::class)
        val pipePath = """\\.\pipe\shareplay\mpv\${Uuid.random()}"""

        process = ProcessBuilder(
            "mpv",
            "--input-ipc-server=$pipePath",
            "--force-window",
            "--idle"
        ).redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()

        val pipeFile = File(pipePath)
        while (!pipeFile.exists()) sleep(10)

        val pipeSocket: Socket = Win32NamedPipeSocket(pipePath)

        writer = PrintWriter(pipeSocket.outputStream, true)
        reader = pipeSocket.inputStream.bufferedReader()
    }

    fun start() = runBlocking {
        launch(Dispatchers.IO) {
            val requests = listOf(
                IPC.Request.LoadFile(
                    """C:\Users\jmaxi\Mis ficheros\Anime\[Trix] Porco Rosso (1992) (BD 1080p AV1) [E78BBC59].mkv"""
                ),
                IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, 100),
                IPC.Request.ObserveProperty(42, IPC.Property.VOLUME),
                IPC.Request.SetProperty(IPC.Property.VOLUME, 0),
                IPC.Request.SetProperty(IPC.Property.PAUSE, true),
                IPC.Request.GetProperty(IPC.Property.PLAYBACK_TIME),
            )

            requests.forEach { request ->
                print("Press Enter to send request ${request.toJsonString()}:")
                readln()

                writer.writeRequest(request)
                println("Request sent: ${request.toJsonString()}")
            }
        }

        launch(Dispatchers.IO) {
            while (true) {
                val line = reader.readLine()
                println("Response received: $line")
            }
        }
    }

    fun PrintWriter.writeRequest(request: IPC.Request) = println(request.toJsonString())
}

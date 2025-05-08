package tfg.proto.shareplay

import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.scalasbt.ipcsocket.Win32NamedPipeSocket
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun main() = coroutineScope {
    val requests = produce {
        send(IPC.Request.LoadFile(
            """C:\Users\jmaxi\Mis ficheros\Anime\[Trix] Porco Rosso (1992) (BD 1080p AV1) [E78BBC59].mkv"""
        ))
        delay(500)
        while (true) {
            send(IPC.Request.SetProperty(IPC.Property.VOLUME, 0))
            delay(500)
        }
    }

    val mpv = MPV(requests, this)

    mpv.responses.consumeEach { println("Received response: $it") }
}

class MPV(
    private val requests: ReceiveChannel<IPC.Request>,
    coroutineScope: CoroutineScope
) {
    private val process: Process
    private val writer: PrintWriter
    private val reader: BufferedReader
    val responses: ReceiveChannel<JsonObject>

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

        responses = coroutineScope.responsesProducer()

        coroutineScope.launch {
            requests.consumeEach { request ->
                writer.writeRequest(request)
                withContext(Dispatchers.IO) {
                    println("Sent request: ${request.toJsonString()}")
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.responsesProducer() = produce {
        while (true) send(reader.readResponse())
    }

    private suspend fun BufferedReader.readResponse(): JsonObject {
        val line = withContext(Dispatchers.IO) { readLine() }
        return Json.parseToJsonElement(line).jsonObject
    }

    private suspend fun PrintWriter.writeRequest(request: IPC.Request) = withContext(Dispatchers.IO) {
        println(request.toJsonString())
    }
}

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.scalasbt.ipcsocket.Win32NamedPipeSocket
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

suspend fun main() {
    val mpv = MPV()

    mpv.join()
}

class MPV {
    private val process: Process
    private val writer: PrintWriter
    private val reader: BufferedReader

    private val job: Job

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

        job = start()
    }

    private fun start() = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
        launch {
            val requests = requestsProducer()

            requests.consumeEach {
                writer.writeRequest(it)
                withContext(Dispatchers.IO) { println("Request sent: ${it.toJsonString()}") }
                delay(1500)
            }
        }

        launch {
            val responses = responsesProducer()

            responses.consumeEach {
                withContext(Dispatchers.IO) { println("Received response: $it") }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.requestsProducer() = produce {
        listOf(
            IPC.Request.LoadFile(
                """C:\Users\jmaxi\Mis ficheros\Anime\[Trix] Porco Rosso (1992) (BD 1080p AV1) [E78BBC59].mkv"""
            ),
            IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, 100),
            IPC.Request.ObserveProperty(42, IPC.Property.VOLUME),
            IPC.Request.SetProperty(IPC.Property.VOLUME, 0),
            IPC.Request.SetProperty(IPC.Property.PAUSE, true),
            IPC.Request.GetProperty(IPC.Property.PLAYBACK_TIME),
        ).forEach { send(it) }
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

    suspend fun join() {
        job.join()
    }
}

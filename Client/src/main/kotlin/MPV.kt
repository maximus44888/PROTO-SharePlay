package tfg.proto.shareplay

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
    val mpv = MPV()

    mpv.start()
}

fun FileChannel.write(message: String) {
    val buffer = ByteBuffer.wrap((message + "\n").toByteArray(StandardCharsets.UTF_8))
    while (buffer.hasRemaining()) {
        write(buffer)
    }
}

fun FileChannel.read(): String {
    val buffer = ByteBuffer.allocate(4096)
    val stringBuilder = StringBuilder()

    try {
        val bytesRead = read(buffer)
        if (bytesRead > 0) {
            buffer.flip()
            val bytes = ByteArray(bytesRead)
            buffer.get(bytes)
            stringBuilder.append(String(bytes, StandardCharsets.UTF_8))
        }
    } catch (e: IOException) {
        println("Error reading from channel: ${e.message}")
    }

    return stringBuilder.toString()
}

class MPV {
    private val process: Process
    private val pipe: FileChannel

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
        while (!pipeFile.exists()) {
        }

        this.pipe = RandomAccessFile(pipeFile, "rw").channel
    }

    @Serializable
    data class Command(
        private val command: List<String>,
        @SerialName("request_id") private val requestId: Int? = null
    ) {
        fun encodeToJsonString() = Json.encodeToString(this)
    }

    @Serializable
    data class Response(
        private val error: String,
        private val data: String? = null,
        private val async: Boolean? = null,
        @SerialName("request_id") private val requestId: Int? = null
    ) {
        fun encodeToJsonString() = Json.encodeToString(this)
    }

    @Serializable
    data class Event(
        private val event: String,
        private val data: String? = null,
        private val id: Int? = null,
        private val name: String? = null,
        @SerialName("request_id") private val requestId: Int? = null
    ) {
        fun encodeToJsonString() = Json.encodeToString(this)
    }

    fun start() {
        val listOfCommands = listOf(
            listOf(
                "set_property",
                "volume",
                0.toString()
            ),
            listOf(
                "loadfile",
                """C:\Users\jmaxi\Mis ficheros\Anime\Pop.Team.Epic.S01.1080p.BluRay.DUAL.Opus5.1.H.264-DemiHuman\Pop.Team.Epic.S01E02.1080p.BluRay.DUAL.Opus5.1.H.264-DemiHuman.mkv"""
            ),
        )
        val commands = listOfCommands.mapIndexed { index, command ->
            Command(
                command = command,
                requestId = index
            )
        }

        runBlocking {
            val rawResponses = rawResponsesProducer()

            launch {
                commands.forEach { command ->
                    val jsonCommand = command.encodeToJsonString()
                    println("Sent command: $jsonCommand")
                    pipe.write(jsonCommand)
                }
            }

            for (rawResponse in rawResponses) {
                if (rawResponse.isEmpty()) continue
                println("Received response: $rawResponse")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun CoroutineScope.rawResponsesProducer() = produce {
        while (isActive) withContext(Dispatchers.IO) { pipe.read() }.split('\n').forEach { send(it) }
    }
}

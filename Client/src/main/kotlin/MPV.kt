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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
    val mpv = MPV()

    mpv.start()
}

fun FileChannel.write(command: MPV.Command) {
    val jsonCommand = command.encodeToJsonString()
    val buffer = ByteBuffer.wrap((jsonCommand + "\n").toByteArray(StandardCharsets.UTF_8))
    while (buffer.hasRemaining()) {
        write(buffer)
    }
    println("Sent command: $jsonCommand")
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
        private val command: List<JsonElement>,
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
                JsonPrimitive("loadfile"),
                JsonPrimitive("""C:\Users\jmaxi\Mis ficheros\Anime\Pop.Team.Epic.S01.1080p.BluRay.DUAL.Opus5.1.H.264-DemiHuman\Pop.Team.Epic.S01E02.1080p.BluRay.DUAL.Opus5.1.H.264-DemiHuman.mkv""")
            ),
            listOf(
                JsonPrimitive("set_property"),
                JsonPrimitive("volume"),
                JsonPrimitive(0)
            ),
            listOf(
                JsonPrimitive("set_property"),
                JsonPrimitive("pause"),
                JsonPrimitive(true)
            ),
            listOf(
                JsonPrimitive("seek"),
                JsonPrimitive(500)
            )
        )
        val commands = listOfCommands.mapIndexed { index, command ->
            Command(
                command = command,
                requestId = index
            )
        }

        runBlocking {
            val rawResponses = rawResponsesProducer()

            commands.forEach { command ->
                print("Press Enter to send next command...")
                readln()
                pipe.write(command)
            }

            for (rawResponse in rawResponses) {
                if (rawResponse.isEmpty()) continue
                withContext(Dispatchers.IO) { println("Received response: $rawResponse") }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun CoroutineScope.rawResponsesProducer() = produce {
        while (isActive) pipe.read().split('\n').forEach { send(it) }
    }
}

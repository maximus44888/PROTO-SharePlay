package tfg.proto.shareplay

import java.io.File
import java.io.RandomAccessFile
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

var request_id = 100
const val mpv = """mpv"""
const val PIPE_PATH = """\\.\pipe\shareplay\mpv"""
const val MEDIA_PATH =
    """C:\Users\jmaxi\Mis ficheros\Anime\Pop.Team.Epic.S01.1080p.BluRay.DUAL.Opus5.1.H.264-DemiHuman\Pop.Team.Epic.S01E01.1080p.BluRay.DUAL.Opus5.1.H.264-DemiHuman.mkv"""

const val ANSI_GREEN = "\u001B[32m"
const val ANSI_ORANGE = "\u001B[33m"
const val ANSI_BLUE = "\u001B[34m"
const val ANSI_RESET = "\u001B[0m"

fun main() {
    ProcessBuilder(mpv, "--input-ipc-server=$PIPE_PATH", MEDIA_PATH)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()

    val pipe = File(PIPE_PATH)
    while (!pipe.exists()) sleep(10)

    val pipeChannel = RandomAccessFile(pipe, "rw").channel

    runBlocking {
        launch {
            while (true) {
                withContext(Dispatchers.IO) {
                    pipeChannel.read()
                }
            }
        }

        listOf("volume", "pause").forEachIndexed { id, property ->
            val commandList = listOf(
                JsonPrimitive("observe_property"),
                JsonPrimitive(id),
                JsonPrimitive(property)
            )
            val cmd = MpvCommand(command = JsonArray(commandList), request_id = request_id++)
            pipeChannel.write(cmd)
        }

        launch {
            while (true) {
                val input = withContext(Dispatchers.IO) {
                    readln()
                }

                val commandList = when {
                    input == "pause" -> listOf(
                        JsonPrimitive("set_property"),
                        JsonPrimitive("pause"),
                        JsonPrimitive(true)
                    )

                    input == "resume" -> listOf(
                        JsonPrimitive("set_property"),
                        JsonPrimitive("pause"),
                        JsonPrimitive(false)
                    )

                    input.startsWith("seek ") -> listOf(
                        JsonPrimitive("set_property"),
                        JsonPrimitive("time-pos"),
                        JsonPrimitive(input.removePrefix("seek ").trim().toDouble())
                    )

                    else -> {
                        println("Couldn't build command. Use: pause, resume, seek <time>")
                        continue
                    }
                }

                val cmd = MpvCommand(
                    command = JsonArray(commandList),
                    request_id = request_id++
                )
                println("${ANSI_BLUE}Built command: $cmd$ANSI_RESET")
                withContext(Dispatchers.IO) { pipeChannel.write(cmd) }
            }
        }
    }
}

@Serializable
data class MpvCommand(
    val command: JsonArray,
    val request_id: Int
)

fun FileChannel.write(command: MpvCommand) {
    val json = Json.encodeToString(command)
    val buffer = ByteBuffer.wrap((json + if (json.endsWith('\n')) "" else '\n').toByteArray(StandardCharsets.UTF_8))
    write(buffer)
    println("$ANSI_GREEN→ $json$ANSI_RESET")
}

fun FileChannel.read(): List<String>? {
    val buffer = ByteBuffer.allocate(1024)

    val bytesRead = read(buffer)
    if (bytesRead <= 0) return null
    buffer.flip()

    val messages = String(buffer.array(), 0, bytesRead, StandardCharsets.UTF_8).split('\n').filter { it.isNotBlank() }
    messages.forEach { println("$ANSI_ORANGE← $it$ANSI_RESET") }

    return messages
}

package tfg.proto.shareplay

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

const val mpv = "D:\\mpv\\mpv.exe"
const val PIPE_PATH = "\\\\.\\pipe\\shareplay\\mpv"
const val MEDIA_PATH = "D:\\Downloads\\Berserk\\Berserk 01.mp4"

fun main() {
    val mpvProcess = ProcessBuilder(mpv, "--input-ipc-server=$PIPE_PATH", MEDIA_PATH)
        .redirectErrorStream(true)
        .start()

    sleep(2000) // Wait for MPV to start and create the pipe

    RandomAccessFile(PIPE_PATH, "rw").channel.use { fileChannel ->

        // Observable comand
        val propertiesToObserve = listOf("volume", "pause", "playback-time")
        propertiesToObserve.forEachIndexed { index, prop ->
            val commandList = listOf(
                JsonPrimitive("observe_property"),
                JsonPrimitive(index + 1),
                JsonPrimitive(prop)
            )
            val cmd = MpvCommand(command = JsonArray(commandList), request_id = 100 + index)
            val jsonCmd = Json.encodeToString(MpvCommand.serializer(), cmd)
            fileChannel.write(jsonCmd)
        }

        while (true) {
            val response = fileChannel.read()
            if (response.isNotBlank()) {
                try {
                    val json = Json.parseToJsonElement(response)
                    val obj = json.jsonObject

                    if (obj["event"]?.jsonPrimitive?.content == "property-change") {
                        val name = obj["name"]?.jsonPrimitive?.content
                        val data = obj["data"]
                        println("Change in '$name': $data")
                    } else {
                        println("Other message: $response")
                    }
                } catch (e: Exception) {
                    println("Error JSON: $response")
                }
            }
        }
        mpvProcess.waitFor()
    }

}

//@Serializable
//data class MpvCommand(val command: List<String>, val request_id: Int)

@Serializable
data class MpvCommand(
    val command: JsonArray,
    val request_id: Int
)

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
        var bytesRead = read(buffer)
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

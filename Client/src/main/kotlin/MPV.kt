package tfg.proto.shareplay

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

const val mpv = "mpv"
const val PIPE_PATH = "\\\\.\\pipe\\shareplay\\mpv"
const val MEDIA_PATH = "C:\\Users\\jmaxi\\Mis ficheros\\Anime\\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\\S01E01-Undertaker [2F703024].mkv"

fun main() {
    val mpvProcess = ProcessBuilder(mpv, "--input-ipc-server=$PIPE_PATH", MEDIA_PATH)
        .redirectErrorStream(true)
        .start()

    sleep(2000) // Wait for MPV to start and create the pipe

    RandomAccessFile(PIPE_PATH, "rw").channel.use { fileChannel ->
        while (true) {
            readln()


            val getPlaybackTimeCommand = MpvCommand(listOf("get_property", "playback-time"), 1)
            val getPlaybackTimeCommandJson = Json.encodeToString(getPlaybackTimeCommand) + "\n"

            fileChannel.write(getPlaybackTimeCommandJson)
            println("Sent command: $getPlaybackTimeCommandJson")

            val getPlaybackTimeResponse = fileChannel.read()
            println("Received response: $getPlaybackTimeResponse")


            val setVolumeCommand = MpvCommand(listOf("set_property", "volume", "50"), 2)
            val setVolumeCommandJson = Json.encodeToString(setVolumeCommand) + "\n"
            fileChannel.write(setVolumeCommandJson)
            println("Sent command: $setVolumeCommandJson")

            val setVolumeResponse = fileChannel.read()
            println("Received response: $setVolumeResponse")

        }
        mpvProcess.waitFor()
    }

}

@Serializable
data class MpvCommand(val command: List<String>, val request_id: Int)

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

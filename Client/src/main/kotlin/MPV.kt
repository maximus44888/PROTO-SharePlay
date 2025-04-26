package tfg.proto.shareplay

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
    val mpv = MPV()

    mpv.start()
}

fun WritableByteChannel.write(request: IPC.Request) {
    val jsonCommand = request.toJsonString()
    val buffer = ByteBuffer.wrap((jsonCommand + "\n").toByteArray(StandardCharsets.UTF_8))
    while (buffer.hasRemaining()) {
        write(buffer)
    }
}

fun ReadableByteChannel.readLine(): String? {
    val baos = ByteArrayOutputStream()
    val one = ByteBuffer.allocate(1)
    var foundAny = false

    while (true) {
        one.clear()
        val bytesRead = read(one)
        if (bytesRead < 0) {
            // EOF
            return if (foundAny) baos.toString(StandardCharsets.UTF_8) else null
        }
        foundAny = true
        one.flip()
        val b = one.get()
        baos.write(b.toInt())
        if (b == '\n'.code.toByte()) break
    }

    return baos.toString(StandardCharsets.UTF_8)
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

    fun start() {
        val commands = listOf(
            IPC.Request.LoadFile(
                """C:\Users\jmaxi\Mis ficheros\Anime\[Trix] Porco Rosso (1992) (BD 1080p AV1) [E78BBC59].mkv""",
                requestId = 0
            ),
            IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, 100, requestId = 1),
            IPC.Request.ObserveProperty(42, IPC.Property.VOLUME, requestId = 2),
            IPC.Request.SetProperty(IPC.Property.VOLUME, 0, requestId = 3),
            IPC.Request.SetProperty(IPC.Property.PAUSE, true, requestId = 4),
            IPC.Request.GetProperty(IPC.Property.PLAYBACK_TIME, requestId = 5),
        )

        runBlocking {
            val rawResponses = rawResponsesProducer()

            val writer = launch {
                commands.forEach { command ->
                    print("Press Enter to send next command...")
                    readln()
                    pipe.write(command)
                }
            }

            val reader = launch {
                for (rawResponse in rawResponses) {
                    if (rawResponse.isEmpty()) continue
                    withContext(Dispatchers.IO) { println("Received response: $rawResponse") }
                }
            }

            withContext(Dispatchers.IO) { process.waitFor() }
            writer.cancel()
            reader.cancel()
            rawResponses.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun CoroutineScope.rawResponsesProducer() = produce {
        while (true) {
            val line = pipe.readLine()
            if (line == null) break
            if (line.isNotBlank()) send(line)
        }
    }
}

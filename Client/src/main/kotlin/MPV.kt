package tfg.proto.shareplay

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
    val mpv = MPV()

    mpv.start()
}

class MPV {
    private val process: Process
    private val readPipe: ReadableByteChannel
    private val writePipe: WritableByteChannel

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

        val pipe = RandomAccessFile(pipeFile, "rw").channel

        this.writePipe = pipe
        this.readPipe = pipe
    }

    fun start() {
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

        requests.forEach {
            readln()
            println(it.toJsonString())
        }
    }
}

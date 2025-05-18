package tfg.proto.shareplay

import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.scalasbt.ipcsocket.Win32NamedPipeSocket
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MPV(
    private val mpvPath: String
) : Player {
    private val writer: PrintWriter
    private val reader: BufferedReader
    private val responses: ReceiveChannel<JsonObject>
    private val completables = mutableMapOf<Int, CompletableDeferred<JsonObject>>()

    init {
        @OptIn(ExperimentalUuidApi::class)
        val pipePath = """\\.\pipe\shareplay\mpv\${Uuid.random()}"""

        ProcessBuilder(
            mpvPath,
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

        responses = CoroutineScope(Dispatchers.IO).produce {
            while (true) send(reader.readResponse())
        }

        CoroutineScope(Dispatchers.IO).launch {
            for (response in responses) {
                response["request_id"]?.jsonPrimitive?.intOrNull.let { requestId -> 
                    completables[requestId]?.complete(response)
                    completables.remove(requestId)
                }
                println(response)
            }
        }
    }

    private suspend fun BufferedReader.readResponse() = withContext(Dispatchers.IO) { Json.parseToJsonElement(readLine()).jsonObject }

    private suspend fun PrintWriter.writeRequest(request: IPC.Request) = withContext(Dispatchers.IO) {
        completables[request.requestId] = CompletableDeferred()
        println(request.toJsonString())
        completables[request.requestId]?.await()
    }

    override suspend fun loadFile(fileIdentifier: String) {
        TODO("Not yet implemented")
    }

    override suspend fun pause() {
        writer.writeRequest(IPC.Request.SetProperty(IPC.Property.PAUSE, true))
    }

    override suspend fun play() {
        writer.writeRequest(IPC.Request.SetProperty(IPC.Property.PAUSE, false))
    }

    override suspend fun jumpTo(time: Int) {
        writer.writeRequest(IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, time))
    }
}

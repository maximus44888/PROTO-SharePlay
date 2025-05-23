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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.scalasbt.ipcsocket.Win32NamedPipeSocket
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MPV(
    mpvPath: String
) : Player {
    private val writer: PrintWriter
    private val reader: BufferedReader
    private val responses: ReceiveChannel<JsonObject>
    private val requests = mutableMapOf<Int, CompletableDeferred<JsonObject>>()
    private val observedProperties = mutableMapOf<Int, Channel<JsonObject>>()
    private val observedEvents = mutableMapOf<IPC.Event.Type, Channel<JsonObject>>()

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
                launch(Dispatchers.IO) {
                    response["request_id"]?.jsonPrimitive?.intOrNull?.let { requestId ->
                        requests[requestId]?.complete(response)
                        requests.remove(requestId)
                    }
                    response["id"]?.jsonPrimitive?.intOrNull?.let { id ->
                        observedProperties[id]?.send(response)
                    }
                    val event = response["event"]?.jsonPrimitive?.contentOrNull
                    observedEvents.forEach { (eventType, channel) ->
                        if (eventType.value == event) channel.send(response)
                    }
                }
            }
        }
    }

    private suspend fun BufferedReader.readResponse() =
        withContext(Dispatchers.IO) { Json.parseToJsonElement(readLine()).jsonObject }

    private suspend fun PrintWriter.writeRequest(request: IPC.Request) = withContext(Dispatchers.IO) {
        val completableDeferred = CompletableDeferred<JsonObject>()
        requests[request.requestId] = completableDeferred
        println(request.toJsonString())
        completableDeferred.await()
    }

    override suspend fun loadFile(fileIdentifier: String) {
        writer.writeRequest(IPC.Request.LoadFile(fileIdentifier))
    }

    override suspend fun pause(pause: Boolean) {
        writer.writeRequest(IPC.Request.SetProperty(IPC.Property.PAUSE, pause))
    }

    override suspend fun jumpTo(time: Double) {
        writer.writeRequest(IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, time))
    }

    suspend fun getPlaybackTime(): Double? {
        val request = IPC.Request.GetProperty(IPC.Property.PLAYBACK_TIME)
        val response = writer.writeRequest(request)
        return response["data"]?.jsonPrimitive?.doubleOrNull
    }

    override suspend fun observePause(): ReceiveChannel<Boolean> {
        val request = IPC.Request.ObserveProperty(IPC.Property.PAUSE)
        val channel = Channel<JsonObject>()
        observedProperties[request.id] = channel
        writer.writeRequest(request)

        return channel.map { it?.get("data")?.jsonPrimitive?.booleanOrNull }.filterNotNull()
    }

    override suspend fun observeSeek(): ReceiveChannel<Double> {
        val channel = Channel<JsonObject>()
        observedEvents[IPC.Event.Type.SEEK] = channel

        return channel.map { getPlaybackTime() }.filterNotNull()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> ReceiveChannel<T?>.map(transform: suspend (T?) -> R) =
    CoroutineScope(Dispatchers.IO).produce {
        for (item in this@map) {
            send(transform(item))
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> ReceiveChannel<T?>.filterNotNull(): ReceiveChannel<T> =
    CoroutineScope(Dispatchers.IO).produce {
        for (item in this@filterNotNull) {
            if (item != null) send(item)
        }
    }

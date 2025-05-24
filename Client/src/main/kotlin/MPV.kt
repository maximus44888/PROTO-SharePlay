package tfg.proto.shareplay

import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
    private val responses: SharedFlow<JsonObject>
    private val requests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

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

        responses = flow {
            while (true) {
                val responseLine = withContext(Dispatchers.IO) { reader.readLine() }
                emit(Json.parseToJsonElement(responseLine).jsonObject)
            }
        }.shareIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, 0)

        CoroutineScope(Dispatchers.IO).launch {
            responses.collect {
                val requestId = it["request_id"]?.jsonPrimitive?.intOrNull
                if (requestId != null) {
                    requests[requestId]?.complete(it)
                    requests.remove(requestId)
                }
            }
        }
    }

    private suspend fun IPC.Request.execute(): JsonObject {
        val completableDeferred = CompletableDeferred<JsonObject>()
        requests[requestId] = completableDeferred
        writer.println(jsonString)
        return completableDeferred.await()
    }

    override suspend fun loadFile(fileIdentifier: String) {
        IPC.Request.LoadFile(fileIdentifier).execute()
    }

    override suspend fun pause(pause: Boolean) {
        IPC.Request.SetProperty(IPC.Property.PAUSE, pause).execute()
    }

    override suspend fun jumpTo(time: Double) {
        IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, time).execute()
    }

    suspend fun getPlaybackTime(): Double? {
        val response = IPC.Request.GetProperty(IPC.Property.PLAYBACK_TIME).execute()
        return response["data"]?.jsonPrimitive?.doubleOrNull
    }

    override suspend fun observePause(): SharedFlow<Boolean> {
        val property = IPC.Property.PAUSE
        val request = IPC.Request.ObserveProperty(property)

        return responses
            .onStart { request.execute() }
            .filter {
                it["id"]?.jsonPrimitive?.intOrNull == request.id && it["name"]?.jsonPrimitive?.content == property.value
            }.map {
                it["data"]?.jsonPrimitive?.booleanOrNull
            }.filterNotNull().shareIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, 0)
    }

    override suspend fun observeSeek(): SharedFlow<Double> {
        return responses
            .filter { it["event"]?.jsonPrimitive?.content == IPC.EventType.SEEK.value }
            .map { getPlaybackTime() }
            .filterNotNull()
            .shareIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, 0)
    }
}

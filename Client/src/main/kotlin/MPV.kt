package tfg.proto.shareplay

import java.io.File
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.scalasbt.ipcsocket.Win32NamedPipeSocket
import tfg.proto.shareplay.Player.Companion.buildURI
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndDecrement
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
class MPV(
    mpvPath: String
) : Player {
    private val process: Process
    private val incoming: SharedFlow<JsonObject>
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val execute: suspend IPC.Request.() -> JsonObject
    private val scope = CoroutineScope(Dispatchers.IO) + SupervisorJob()
    override val durationUnit = DurationUnit.SECONDS
    private val skips = ConcurrentHashMap<String, AtomicInt>()

    init {
        @OptIn(ExperimentalUuidApi::class)
        val pipePath = """\\.\pipe\shareplay\mpv\${Uuid.random()}"""

        process = ProcessBuilder(
            mpvPath,
            "--input-ipc-server=$pipePath",
            "--force-window",
            "--idle"
        ).redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()

        File(pipePath).run {
            while (!exists()) sleep(10)
        }

        val pipeSocket: Socket = Win32NamedPipeSocket(pipePath)

        val writer = PrintWriter(pipeSocket.outputStream, true)

        execute = {
            val deferred = CompletableDeferred<JsonObject>()
            pending[requestId] = deferred
            writer.println(jsonString)
            deferred.await().also { pending.remove(requestId) }
        }

        incoming = flow {
            pipeSocket.inputStream.bufferedReader().use {
                while (true) {
                    val responseLine = withContext(Dispatchers.IO) { it.readLine() }
                    val responseJson = Json.parseToJsonElement(responseLine).jsonObject
                    responseJson["request_id"]?.jsonPrimitive?.intOrNull?.let { requestId ->
                        pending[requestId]?.complete(responseJson)
                        pending.remove(requestId)
                    } ?: emit(responseJson)
                }
            }
        }.shareIn(scope, SharingStarted.Eagerly, 0)
        CoroutineScope(Dispatchers.IO).launch { pause() }
    }

    fun shouldSkip(eventType: String): Boolean {
        return skips.computeIfAbsent(eventType) { AtomicInt(0) }.run {
            if (load() > 0) {
                fetchAndDecrement()
                true
            } else {
                false
            }
        }
    }

    override suspend fun loadMedia(mediaURI: URI) {
        skips.computeIfAbsent(IPC.EventType.FILE_LOADED.value) { AtomicInt(0) }.fetchAndIncrement()
        incoming
            .onStart { IPC.Request.LoadFile(mediaURI.toString()).execute() }
            .first { it["event"]?.jsonPrimitive?.content == IPC.EventType.FILE_LOADED.value }
    }

    suspend fun getMedia(): URI? {
        val response = IPC.Request.GetProperty(IPC.Property.PATH).execute()
        return response["data"]?.jsonPrimitive?.contentOrNull?.let { buildURI(it) }
    }

    override suspend fun resume() {
        skips.computeIfAbsent(IPC.Property.PAUSE.value) { AtomicInt(0) }.fetchAndIncrement()
        IPC.Request.SetProperty(IPC.Property.PAUSE, false).execute()
    }

    override suspend fun pause() {
        skips.computeIfAbsent(IPC.Property.PAUSE.value) { AtomicInt(0) }.fetchAndIncrement()
        IPC.Request.SetProperty(IPC.Property.PAUSE, true).execute()
    }

    override suspend fun seek(time: Duration) {
        skips.computeIfAbsent(IPC.EventType.SEEK.value) { AtomicInt(0) }.fetchAndIncrement()
        IPC.Request.SetProperty(IPC.Property.PLAYBACK_TIME, time.toDouble(durationUnit)).execute()
    }

    suspend fun getPlaybackTime(): Duration? {
        val response = IPC.Request.GetProperty(IPC.Property.PLAYBACK_TIME).execute()
        return response["data"]?.jsonPrimitive?.doubleOrNull?.toDuration(durationUnit)
    }

    override val loadedMediaEvents: SharedFlow<URI> = run {
        incoming
            .filter { it["event"]?.jsonPrimitive?.content == IPC.EventType.FILE_LOADED.value }
            .filterNot { shouldSkip(IPC.EventType.FILE_LOADED.value) }
            .mapNotNull { getMedia() }
            .shareIn(scope, SharingStarted.Eagerly, 0)
    }

    override val pauseEvents: SharedFlow<Boolean> = run {
        val property = IPC.Property.PAUSE
        val request = IPC.Request.ObserveProperty(property)

        incoming
            .onStart { request.execute() }
            .filter { it["id"]?.jsonPrimitive?.intOrNull == request.id }
            .filterNot { shouldSkip(property.value) }
            .mapNotNull { it["data"]?.jsonPrimitive?.booleanOrNull }
            .shareIn(scope, SharingStarted.Eagerly, 0)
    }

    override val seekEvents: SharedFlow<Duration> = run {
        incoming
            .filter { it["event"]?.jsonPrimitive?.content == IPC.EventType.SEEK.value }
            .filterNot { shouldSkip(IPC.EventType.SEEK.value) }
            .mapNotNull { getPlaybackTime() }
            .shareIn(scope, SharingStarted.Eagerly, 0)
    }

    override fun close() {
        process.destroy()
        scope.cancel(CancellationException("MPV closed"))
    }

    object IPC {
        internal enum class Command(val value: String) {
            GET_PROPERTY("get_property"),
            SET_PROPERTY("set_property"),
            OBSERVE_PROPERTY("observe_property"),
            LOAD_FILE("loadfile"),
        }

        enum class Property(val value: String) {
            PATH("path"),
            PAUSE("pause"),
            PLAYBACK_TIME("playback-time"),
        }

        @OptIn(ExperimentalAtomicApi::class)
        sealed class Request(
            internal val command: Command,
            internal val parameters: List<Any>,
            internal open val async: Boolean? = null,
        ) {
            private companion object {
                private val _requestId = AtomicInt(0)
            }

            val requestId = _requestId.fetchAndIncrement()

            @OptIn(ExperimentalSerializationApi::class)
            val jsonString = buildJsonObject {
                putJsonArray("command") {
                    add(command.value)
                    parameters.forEach { param ->
                        when (param) {
                            is String -> add(param)
                            is Number -> add(param)
                            is Boolean -> add(param)
                            is Property -> add(param.value)
                        }
                    }
                }
                put("request_id", requestId)
                async?.let { put("async", it) }
            }.toString()

            data class GetProperty(
                internal val property: Property,
                override val async: Boolean? = null,
            ) : Request(
                command = Command.GET_PROPERTY,
                parameters = listOf(property),
            )

            data class SetProperty(
                internal val property: Property,
                internal val value: Any,
                override val async: Boolean? = null,
            ) : Request(
                command = Command.SET_PROPERTY,
                parameters = listOf(property, value),
            )

            @ConsistentCopyVisibility
            @OptIn(ExperimentalAtomicApi::class)
            data class ObserveProperty private constructor(
                val id: Int,
                private val property: Property,
                override val async: Boolean? = null,
            ) : Request(
                command = Command.OBSERVE_PROPERTY,
                parameters = listOf(id, property),
            ) {
                constructor(property: Property, async: Boolean? = null) : this(
                    id = _id.fetchAndIncrement(),
                    property = property,
                    async = async
                )

                private companion object {
                    private val _id = AtomicInt(1)
                }
            }

            data class LoadFile(
                internal val filePath: String,
                override val async: Boolean? = null,
            ) : Request(
                command = Command.LOAD_FILE,
                parameters = listOf(filePath),
            )
        }

        enum class EventType(val value: String) {
            SEEK("seek"),
            FILE_LOADED("file-loaded"),
        }
    }
}

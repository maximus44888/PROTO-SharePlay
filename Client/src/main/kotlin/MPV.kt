package tfg.proto.shareplay

import java.io.File
import java.io.PrintWriter
import java.lang.Thread.sleep
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.scalasbt.ipcsocket.Win32NamedPipeSocket
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MPV(
    mpvPath: String
) : Player {
    private val incoming: SharedFlow<JsonObject>
    private val execute: suspend IPC.Request.() -> JsonObject

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

        val writer = PrintWriter(pipeSocket.outputStream, true)

        execute = {
            incoming
                .onStart { writer.println(jsonString) }
                .first { it["request_id"]?.jsonPrimitive?.intOrNull == requestId }
        }

        incoming = flow {
            pipeSocket.inputStream.bufferedReader().use {
                while (true) {
                    val responseLine = withContext(Dispatchers.IO) { it.readLine() }
                    emit(Json.parseToJsonElement(responseLine).jsonObject)
                }
            }
        }.shareIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, 0)
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

        return incoming
            .onStart { request.execute() }
            .filter {
                it["id"]?.jsonPrimitive?.intOrNull == request.id && it["name"]?.jsonPrimitive?.content == property.value
            }.map {
                it["data"]?.jsonPrimitive?.booleanOrNull
            }.filterNotNull().shareIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, 0)
    }

    override suspend fun observeSeek(): SharedFlow<Double> {
        return incoming
            .filter { it["event"]?.jsonPrimitive?.content == IPC.EventType.SEEK.value }
            .map { getPlaybackTime() }
            .filterNotNull()
            .shareIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, 0)
    }

    object IPC {
        internal enum class Command(val value: String) {
            GET_PROPERTY("get_property"),
            SET_PROPERTY("set_property"),
            OBSERVE_PROPERTY("observe_property"),
            LOAD_FILE("loadfile"),
        }

        enum class Property(val value: String) {
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
        }
    }
}

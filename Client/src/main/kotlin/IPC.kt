package tfg.proto.shareplay

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object IPC {
    internal enum class Command(val value: String) {
        GET_PROPERTY("get_property"),
        SET_PROPERTY("set_property"),
        OBSERVE_PROPERTY("observe_property"),
        LOAD_FILE("loadfile"),
    }

    enum class Property(val value: String) {
        PAUSE("pause"),
        VOLUME("volume"),
        PLAYBACK_TIME("playback-time"),
    }

    sealed class Request(
        internal val command: Command,
        internal val parameters: List<Any>,
        internal open val async: Boolean? = null,
    ) {
        internal companion object {
            var requestId = 0
                get() = field++
                internal set(value) = Unit
        }

        val requestId = Companion.requestId

        @OptIn(ExperimentalSerializationApi::class)
        fun toJsonString() = buildJsonObject {
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

        data class ObserveProperty private constructor(
            val id: Int,
            private val property: Property,
            override val async: Boolean? = null,
        ) : Request(
            command = Command.OBSERVE_PROPERTY,
            parameters = listOf(id, property),
        ) {
            constructor(property: Property, async: Boolean? = null) : this(Companion.id, property, async)

            internal companion object {
                var id = 1
                    get() = field++
                    internal set(value) = Unit
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

    sealed class Response(
        internal val error: String,
        internal val data: Any? = null,
        internal val requestId: Int? = null,
    )

    sealed class Event(
        internal val type: Type,
        internal open val id: Int? = null,
        internal open val error: String? = null,
    ) {

        data class StartFile(
            internal val playlistEntryId: Int,
            override val id: Int? = null,
            override val error: String? = null,
        ) : Event(
            type = Type.START_FILE,
        )

        data class EndFile(
            internal val reason: Reason,
            internal val playlistEntryId: Int,
            internal val fileError: String? = null,
            internal val playlistInsertId: Int? = null,
            internal val playlistInsertNumEntries: Int? = null,
            override val id: Int? = null,
            override val error: String? = null,
        ) : Event(
            type = Type.END_FILE,
        ) {
            enum class Reason(val value: String) {
                EOF("eof"),
                STOP("stop"),
                QUIT("quit"),
                ERROR("error"),
                REDIRECT("redirect"),
                UNKNOWN("unknown"),
            }
        }

        data class LogMessage(
            internal val prefix: String,
            internal val level: String,
            internal val message: String,
            override val id: Int? = null,
            override val error: String? = null,
        ) : Event(
            type = Type.LOG_MESSAGE,
        )

        data class Hook(
            internal val hookId: Int,
            override val id: Int? = null,
            override val error: String? = null,
        ) : Event(
            type = Type.HOOK,
        )

        data class CommandReply(
            internal val result: Any,
            override val id: Int? = null,
            override val error: String? = null,
        ) : Event(
            type = Type.COMMAND_REPLY,
        )

        data class ClientMessage(
            internal val args: List<String>,
            override val id: Int? = null,
            override val error: String? = null,
        ) : Event(
            type = Type.CLIENT_MESSAGE,
        )

        data class PropertyChange(
            internal val property: Property,
            internal val value: Any,
            override val id: Int? = null,
            override val error: String? = null,
        ) : Event(
            type = Type.PROPERTY_CHANGE,
        )

        enum class Type(val value: String) {
            START_FILE("start-file"),
            END_FILE("end-file"),
            FILE_LOADED("file-loaded"),
            SEEK("seek"),
            PLAYBACK_RESTART("playback-restart"),
            SHUTDOWN("shutdown"),
            LOG_MESSAGE("log-message"),
            HOOK("hook"),
            GET_PROPERTY_REPLY("get-property-reply"),
            SET_PROPERTY_REPLY("set-property-reply"),
            COMMAND_REPLY("command-reply"),
            CLIENT_MESSAGE("client-message"),
            VIDEO_RECONFIG("video-reconfig"),
            AUDIO_RECONFIG("audio-reconfig"),
            PROPERTY_CHANGE("property-change"),
            IDLE("idle"),
            TICK("tick"),
        }
    }

    fun receive(rawJson: String) = Json.decodeFromString<Response>(rawJson)
}

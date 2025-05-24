package tfg.proto.shareplay

import kotlinx.serialization.ExperimentalSerializationApi
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

        data class ObserveProperty(
            private val property: Property,
            override val async: Boolean? = null,
        ) : Request(
            command = Command.OBSERVE_PROPERTY,
            parameters = listOf(id, property),
        ) {
            internal companion object {
                var id = 1
                    get() = field++
                    internal set(value) = Unit
            }

            val id = Companion.id
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

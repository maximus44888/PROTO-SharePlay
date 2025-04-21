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
        VOLUME("volume"),
        PLAYBACK_TIME("playback-time"),
    }

    sealed class Request(
        internal val command: Command,
        internal val parameters: List<Any>,
        internal open val requestId: Int? = null,
        internal open val async: Boolean? = null,
    ) {
        @OptIn(ExperimentalSerializationApi::class)
        fun toJsonString() = buildJsonObject {
            putJsonArray("command") {
                add(command.value)
                parameters.forEach { param ->
                    when (param) {
                        is String -> add(param)
                        is Int -> add(param)
                        is Boolean -> add(param)
                        is Property -> add(param.value)
                    }
                }
            }
            requestId?.let { put("request_id", it) }
            async?.let { put("async", it) }
        }.toString()
    }

    data class GetProperty(
        internal val property: Property,
        override val requestId: Int? = null,
        override val async: Boolean? = null,
    ) : Request(
        command = Command.GET_PROPERTY,
        parameters = listOf(property),
    )

    data class SetProperty(
        internal val property: Property,
        internal val value: Any,
        override val requestId: Int? = null,
        override val async: Boolean? = null,
    ) : Request(
        command = Command.SET_PROPERTY,
        parameters = listOf(property, value),
    )

    data class ObserveProperty(
        internal val id: Int,
        internal val property: Property,
        override val requestId: Int? = null,
        override val async: Boolean? = null,
    ) : Request(
        command = Command.OBSERVE_PROPERTY,
        parameters = listOf(id, property),
    )
    
    data class LoadFile(
        internal val filePath: String,
        override val requestId: Int? = null,
        override val async: Boolean? = null,
    ) : Request(
        command = Command.LOAD_FILE,
        parameters = listOf(filePath),
    )
}

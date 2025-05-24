package tfg.proto.shareplay

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

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

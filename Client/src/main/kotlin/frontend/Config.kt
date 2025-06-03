package tfg.proto.shareplay.frontend

import java.nio.file.Paths
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/**
 * Data class que representa la configuración del usuario para SharePlay.
 *
 * @property dirServer La dirección del servidor (URL o IP).
 * @property nickname El apodo o nombre de usuario.
 * @property roomDefault La sala por defecto para conectarse.
 */
@Serializable
data class Config(
    val dirServer: String = "",
    val nickname: String = "",
    val roomDefault: String = ""
) {
    /**
     * Objeto singleton que gestiona la carga, guardado y reseteo
     * de la configuración persistente de SharePlay.
     */
    @OptIn(ExperimentalSerializationApi::class)
    companion object {

        /** Archivo donde se guarda la configuración en el directorio home del usuario */
        private val file = Paths.get(System.getProperty("user.home"), ".sharePlayData.json").toFile()

        /**
         * Carga la configuración guardada desde el archivo JSON.
         *
         * @return Un objeto [Config] con la configuración cargada,
         * o `null` si el archivo no existe o hubo un error durante la carga.
         */
        fun load() = try {
            Json.decodeFromStream<Config>(file.inputStream())
        } catch (_: Exception) {
            null
        }

        /**
         * Guarda la configuración proporcionada en un archivo JSON en disco.
         *
         * @param config La configuración a guardar.
         */
        fun save(config: Config) = try {
            Json.encodeToStream(config, file.outputStream())
        } catch (_: Exception) {
        }

        /**
         * Elimina la configuración guardada, reseteando la configuración a su estado inicial.
         */
        fun reset() {
            if (file.exists()) {
                file.delete()
            }
        }
    }

}

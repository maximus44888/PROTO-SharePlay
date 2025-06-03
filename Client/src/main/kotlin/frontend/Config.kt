package tfg.proto.shareplay.frontend

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Paths

/**
 * Data class que representa la configuración del usuario para SharePlay.
 *
 * @property dirServer La dirección del servidor (URL o IP).
 * @property nickname El apodo o nombre de usuario.
 * @property roomDefault La sala por defecto para conectarse.
 */
data class Config(
    val dirServer: String?,
    val nickname: String?,
    val roomDefault: String?
) {
    /**
     * Objeto singleton que gestiona la carga, guardado y reseteo
     * de la configuración persistente de SharePlay.
     */
    companion object {
        /** Instancia del ObjectMapper de Jackson para serialización/deserialización JSON */
        private val mapper = jacksonObjectMapper()

        /** Archivo donde se guarda la configuración en el directorio home del usuario */
        private val file = Paths.get(System.getProperty("user.home"), ".sharePlayData.json").toFile()

        /**
         * Carga la configuración guardada desde el archivo JSON.
         *
         * @return Un objeto [Config] con la configuración cargada,
         * o `null` si el archivo no existe o hubo un error durante la carga.
         */
        fun load(): Config? {
            if (!file.exists()) return null
            return try {
                mapper.readValue<Config>(file)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * Guarda la configuración proporcionada en un archivo JSON en disco.
         *
         * @param config La configuración a guardar.
         */
        fun save(config: Config) {
            try {
                file.parentFile?.mkdirs()
                mapper.writeValue(file, config)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

package tfg.proto.shareplay.frontend

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Paths

data class GadgetConfig(
    val dirServer: String?,
    val nickname: String?,
    val roomDefault: String?
)

object Gadgets {
    private val mapper = jacksonObjectMapper()
    private val file = Paths.get(System.getProperty("user.home"), ".sharePlayData.json").toFile()

    fun loadConfig(): GadgetConfig? {
        if (!file.exists()) return null
        return try {
            mapper.readValue<GadgetConfig>(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveConfig(config: GadgetConfig) {
        try {
            file.parentFile?.mkdirs()
            mapper.writeValue(file, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetConfig() {
        if (file.exists()) {
            file.delete()
        }
    }
}
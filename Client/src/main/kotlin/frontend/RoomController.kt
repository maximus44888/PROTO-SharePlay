package tfg.proto.shareplay.frontend

import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.util.*
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.Stage
import tfg.proto.shareplay.JavaFX
import tfg.proto.shareplay.MPV
import tfg.proto.shareplay.PlayerClient
import kotlin.concurrent.thread

/**
 * Controlador para la ventana de la sala de SharePlay.
 * Gestiona la interfaz de usuario relacionada con la sala, muestra la lista de integrantes,
 * controla la copia de configuración y permite cerrar la sala.
 */
class RoomController(
    socket: Socket,
    filePath: String,
    val room: String,
    val nickname: String,
) {
    private val playerClient: PlayerClient

    /** Lista observable que contiene los nombres de los integrantes mostrados en el ListView. */
    private val roomInfoItems: ObservableList<String> = FXCollections.observableArrayList()

    init {
        playerClient = PlayerClient(socket, room, nickname, MPV(getMPVPath()))
        if (filePath.isNotBlank()) {
            playerClient.loadMedia(filePath)
        }

        thread(isDaemon = true) {
            while (true) {
                Platform.runLater {
                    roomInfoItems.setAll(playerClient.clients)
                    roomInfoItems.addFirst("$nickname (Tú)")
                }
                Thread.sleep(100)
            }
        }
    }

    /** ListView que muestra la lista de integrantes de la sala. */
    lateinit var listRoomInfo: ListView<String>

    /** Etiqueta que muestra el título de la sala actual. */
    lateinit var labelTitle: Label

    /** Botón para copiar la configuración actual al portapapeles. */
    lateinit var onCopyConfig: Button

    /** Botón para cerrar la ventana y regresar a la vista principal. */
    lateinit var onClose: Button

    fun initialize() {
        labelTitle.text = "Sala $room"
        listRoomInfo.items = roomInfoItems
    }

    /**
     * Copia al portapapeles la configuración actual codificada en Base64.
     * La información original tiene el formato "direcciónServidor|sala".
     * Si no existe configuración, no realiza ninguna acción.
     */
    fun onCopyConfig() {
        val config = Config.load()
        if (config != null) {
            val plainText = "${config.dirServer}|${config.roomDefault}"
            val encodedText = Base64.getEncoder().encodeToString(plainText.toByteArray(Charsets.UTF_8))

            val clipboard = Clipboard.getSystemClipboard()
            val content = ClipboardContent()
            content.putString(encodedText)
            clipboard.setContent(content)
        }
    }

    /**
     * Cierra la ventana actual de la sala y abre la ventana principal de SharePlay.
     * Además, libera los recursos del cliente de la sala cerrando la conexión.
     */
    fun onClose() {
        JavaFX.buildWindow(javaClass.getResource("/frontend/main.fxml")).apply {
            title = "SharePlay"
            isResizable = false
        }.show()

        (onClose.scene.window as Stage).close()

        playerClient.close()
    }

    /**
     * Verifica si el ejecutable mpv.exe existe en el directorio de ejecución. Si no existe,
     * lo extrae desde los recursos del proyecto y lo coloca en el directorio de ejecución.
     *
     * @return Ruta absoluta del archivo mpv.exe.
     * @throws IllegalStateException si no se puede extraer el archivo mpv.exe.
     */
    fun getMPVPath(): String {
        val currentDir = File(System.getProperty("user.dir"))
        val mpvFile = File(currentDir, "mpv.exe")

        if (!mpvFile.exists()) {
            try {
                val resourceStream = javaClass.getResourceAsStream("/mpv/mpv.exe")
                    ?: throw IllegalStateException("No se encontró el recurso mpv.exe en el proyecto.")

                resourceStream.use { input ->
                    FileOutputStream(mpvFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("Error al extraer mpv.exe: ${e.message}", e)
            }
        }

        return mpvFile.absolutePath
    }
}

package tfg.proto.shareplay.frontend

import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import java.util.*
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.Stage
import tfg.proto.shareplay.MPV
import tfg.proto.shareplay.PlayerClient

/**
 * Controlador para la ventana de la sala de SharePlay.
 * Gestiona la interfaz de usuario relacionada con la sala, muestra la lista de integrantes,
 * controla la copia de configuración y permite cerrar la sala.
 */
class RoomController {

    /** Etiqueta que muestra el título de la sala actual. */
    lateinit var labelTitle: Label

    /** Botón para copiar la configuración actual al portapapeles. */
    lateinit var onCopyConfig: Button

    /** Botón para cerrar la ventana y regresar a la vista principal. */
    lateinit var onClose: Button

    /** ListView que muestra la lista de integrantes de la sala. */
    lateinit var listRoomInfo: ListView<String>

    /** Cliente que gestiona la comunicación y reproducción en la sala. */
    private var playerClient: PlayerClient? = null

    /** Lista observable que contiene los nombres de los integrantes mostrados en el ListView. */
    private val roomInfoItems: ObservableList<String> = FXCollections.observableArrayList()

    /**
     * Inicializa el controlador de la sala con los datos necesarios para su funcionamiento.
     *
     * Acciones realizadas:
     * - Asocia el socket de conexión recibido.
     * - Carga la configuración guardada desde disco (si existe).
     * - Actualiza la etiqueta de título con el nombre de la sala.
     * - Extrae y lanza el ejecutable de MPV desde los recursos del proyecto.
     * - Crea e inicializa el cliente [PlayerClient] con el socket, el nombre de la sala, el nickname del usuario y la instancia de MPV.
     * - Si se proporciona un archivo de video no vacío, lo carga automáticamente en el reproductor.
     * - Asocia la lista observable al componente gráfico [listRoomInfo] para mostrar en tiempo real los usuarios conectados.
     * - Inicia un hilo que actualiza continuamente la información de los participantes de la sala.
     *
     * @param socket Socket abierto para comunicación con el servidor.
     * @param filePath Ruta del archivo de video a reproducir. Si está vacío, no se carga ningún archivo.
     */
    fun initData(socket: Socket, filePath: String) {
        val config = Config.load()
        val roomName = config?.roomDefault ?: "Desconocida"
        labelTitle.text = "Sala $roomName"
        val mpvPath = getMPVPath()
        val mpv = MPV(mpvPath)
        playerClient = PlayerClient(socket, config?.roomDefault ?: "", config?.nickname ?: "", mpv)
        listRoomInfo.items = roomInfoItems
        if (filePath.isNotBlank()) {
            playerClient?.loadMedia(filePath)
        }
        @Suppress("UNCHECKED_CAST")
        startRoomInfoUpdater()
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
        val loader = FXMLLoader(javaClass.getResource("/frontend/main.fxml"))
        val root: Parent = loader.load()
        val stage = Stage()
        stage.title = "SharePlay"
        stage.isResizable = false
        stage.scene = Scene(root)
        stage.show()

        (onClose.scene.window as Stage).close()

        playerClient?.let {
            it.close()
            playerClient = null
        }
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

    /**
     * Inicia un hilo en segundo plano que actualiza periódicamente
     * la lista observable con los nombres actuales de los integrantes de la sala.
     */

    private fun startRoomInfoUpdater() {
        val thread = Thread {
            while (playerClient != null) {
                val clientsList = playerClient?.clients ?: emptyList()

                javafx.application.Platform.runLater {
                    roomInfoItems.setAll(clientsList)
                }

                Thread.sleep(100)
            }
        }

        thread.isDaemon = true
        thread.start()
    }
}

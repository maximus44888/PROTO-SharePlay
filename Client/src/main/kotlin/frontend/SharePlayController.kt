package tfg.proto.shareplay.frontend

import java.io.File
import java.net.Socket
import java.net.URI
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.stage.FileChooser
import javafx.stage.Stage

class SharePlayController {

    lateinit var labelTitle: Label
    lateinit var configurationCopied: TextField
    lateinit var validateConfig: Button
    lateinit var filePathField: TextField
    lateinit var roomDefaultField: TextField
    lateinit var nickNameField: TextField
    lateinit var serverPathComboBox: ComboBox<Any>
    lateinit var onBrowserFile: Button

    fun initialize() {
        serverPathComboBox.isEditable = true
        serverPathComboBox.items.addAll(
            "https://45.149.118.37:1234",
            "https://192.168.1.10:5000"
        )
        val config = Gadgets.loadConfig()
        if (config != null) {
            serverPathComboBox.editor.text = config.dirServer ?: ""
            nickNameField.text = config.nickname ?: ""
            roomDefaultField.text = config.roomDefault ?: ""
        }
    }

    fun onBrowserFile() {
        val fileChooser = FileChooser()
        fileChooser.title = "Seleccionar archivo de video"
        fileChooser.extensionFilters.add(
            FileChooser.ExtensionFilter("Archivos de video", "*.mp4", "*.mkv", "*.avi")
        )
        val stage = onBrowserFile.scene.window as Stage
        val selectedFile: File? = fileChooser.showOpenDialog(stage)
        if (selectedFile != null) {
            filePathField.text = selectedFile.absolutePath
        }
    }

    fun onUploadConfig() {
        val input = configurationCopied.text.trim()
        val parts = input.split("|")
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Formato no válido"
            alert.headerText = "Configuración no válida"
            alert.contentText = "El formato debe ser: direcciónServidor|sala"
            alert.showAndWait()
            return
        }
        val server = parts.dropLast(1).joinToString("|")
        val room = parts.last()

        serverPathComboBox.editor.text = server
        roomDefaultField.text = room
        configurationCopied.text = ""
    }

    fun onResetConfig() {
        Gadgets.resetConfig()

        serverPathComboBox.editor.text = ""
        nickNameField.text = ""
        roomDefaultField.text = ""
        configurationCopied.text = ""
    }

    fun onPlaySharePlay() {
        if (!validateRequiredFields()) return

        val config = GadgetConfig(
            dirServer = serverPathComboBox.editor.text,
            nickname = nickNameField.text,
            roomDefault = roomDefaultField.text,
        )
        Gadgets.saveConfig(config)

        val socket: Socket
        try {
            val url = URI(config.dirServer)
            socket = Socket(url.host, url.port)
        } catch (e: Exception) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Error de conexión"
            alert.headerText = "No se pudo conectar con el servidor"
            alert.contentText = "Comprueba que la dirección del servidor sea correcta y que esté en funcionamiento."
            alert.showAndWait()
            return
        }

        val loader = javafx.fxml.FXMLLoader(javaClass.getResource("/roomShareplay.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        val controller = loader.getController<RoomSharePlayController>()
        controller.initData(socket)
        val stage = Stage()
        stage.scene = javafx.scene.Scene(root)
        stage.isResizable = false
        stage.title = "SharePlay"
        stage.show()

        (onBrowserFile.scene.window as Stage).close()
    }

    private fun validateRequiredFields(): Boolean {
        val missingFields = mutableListOf<String>()

        if (serverPathComboBox.editor.text.isNullOrBlank()) {
            missingFields.add("Dirección del servidor")
        }
        if (nickNameField.text.isNullOrBlank()) {
            missingFields.add("Nombre de usuario")
        }
        if (roomDefaultField.text.isNullOrBlank()) {
            missingFields.add("Sala por defecto")
        }

        return if (missingFields.isNotEmpty()) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Campos obligatorios vacíos"
            alert.headerText = "Faltan campos por rellenar"
            alert.contentText = "Debe rellenar los siguientes campos:\n- ${missingFields.joinToString("\n- ")}"
            alert.showAndWait()
            false
        } else {
            true
        }
    }
}

package tfg.proto.shareplay.frontend

import java.io.File
import java.net.Socket
import java.net.URI
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import java.util.Base64
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.stage.FileChooser
import javafx.stage.Stage

/**
 * Controlador para la ventana principal de SharePlay.
 * Permite configurar la conexión al servidor, seleccionar archivo de video,
 * gestionar configuración guardada y lanzar la conexión a la sala compartida.
 */
class SharePlayController {

    /** Etiqueta que muestra el título o encabezado principal de la ventana. */
    lateinit var labelTitle: Label

    /** Campo de texto para pegar o mostrar la configuración en formato "servidor|sala". */
    lateinit var configurationCopied: TextField

    /** Botón que valida y procesa la configuración pegada en [configurationCopied]. */
    lateinit var validateConfig: Button

    /** Campo de texto que muestra la ruta del archivo de video seleccionado. */
    lateinit var filePathField: TextField

    /** Campo de texto para ingresar o mostrar la sala por defecto. */
    lateinit var roomDefaultField: TextField

    /** Campo de texto para ingresar o mostrar el nombre de usuario (nickname) que se utilizará en la sala. */
    lateinit var nickNameField: TextField

    /** Campo editable tipo ComboBox para seleccionar o ingresar la dirección del servidor. */
    lateinit var serverPathComboBox: ComboBox<Any>

    /** Botón que abre el explorador de archivos para seleccionar un archivo de video. */
    lateinit var onBrowserFile: Button

    /**
     * Inicializa el controlador.
     * - Configura el ComboBox del servidor como editable.
     * - Añade direcciones de servidor predefinidas al ComboBox.
     * - Carga la configuración guardada previamente y la muestra en los campos correspondientes.
     * - Añade listeners a los campos de texto y al editor del ComboBox para eliminar
     *   la clase CSS "error" cuando el usuario ingresa un valor no vacío.
     */
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

        listOf(nickNameField, roomDefaultField).forEach { field ->
            field.textProperty().addListener { _, _, newValue ->
                if (!newValue.isNullOrBlank()) {
                    field.styleClass.remove("error")
                }
            }
        }

        serverPathComboBox.editor.textProperty().addListener { _, _, newValue ->
            if (!newValue.isNullOrBlank()) {
                serverPathComboBox.styleClass.remove("error")
            }
        }
    }

    /**
     * Abre un diálogo de selección de archivo para elegir un video con extensiones mp4, mkv o avi.
     * Si se selecciona un archivo, su ruta se asigna al campo [filePathField].
     */
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

    /**
     * Procesa la configuración pegada en [configurationCopied].
     * Espera una cadena codificada en Base64 con el formato original "direcciónServidor|sala".
     * Decodifica el contenido y, si el formato es correcto, actualiza los campos del formulario.
     * En caso de error de decodificación o formato inválido, muestra una alerta indicando que el código es erróneo.
     */
    fun onUploadConfig() {
        val input = configurationCopied.text.trim()
        val decodedText: String

        try {
            decodedText = String(Base64.getDecoder().decode(input), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Código erróneo"
            alert.headerText = "No se pudo procesar la configuración"
            alert.contentText = "El código pegado no es válido o ha sido alterado."
            alert.showAndWait()
            return
        }

        val parts = decodedText.split("|")
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Código erróneo"
            alert.headerText = "No se pudo procesar la configuración"
            alert.contentText = "El código pegado no es válido o ha sido alterado."
            alert.showAndWait()
            return
        }

        val server = parts.dropLast(1).joinToString("|")
        val room = parts.last()

        serverPathComboBox.editor.text = server
        roomDefaultField.text = room
        configurationCopied.text = ""
    }

    /**
     * Restablece la configuración guardada mediante [Gadgets.resetConfig].
     * Además, limpia todos los campos del formulario para que queden vacíos.
     */
    fun onResetConfig() {
        Gadgets.resetConfig()

        serverPathComboBox.editor.text = ""
        nickNameField.text = ""
        roomDefaultField.text = ""
        configurationCopied.text = ""
    }

    /**
     * Valida los campos obligatorios del formulario, guarda la configuración actual,
     * intenta establecer una conexión con el servidor y, si tiene éxito,
     * abre la ventana de la sala compartida.
     *
     * Acciones realizadas:
     * - Comprueba que los campos requeridos (servidor, nickname, sala) no estén vacíos.
     * - Guarda la configuración actual en el archivo local.
     * - Intenta crear un socket TCP con la dirección del servidor proporcionado.
     * - Si falla la conexión, muestra una alerta informativa al usuario.
     * - Si la conexión es exitosa:
     *    - Carga la vista de la sala (roomSharePlay.fxml).
     *    - Inicializa el controlador de la sala con el socket y el archivo de video, si ha sido seleccionado.
     *    - Abre la ventana de la sala.
     *    - Cierra la ventana actual.
     *
     * Nota: El archivo de video solo se carga automáticamente si el campo de ruta no está vacío.
     */
    fun onPlaySharePlay() {
        if (!validateRequiredFields()) return

        val config = GadgetConfig(
            dirServer = serverPathComboBox.editor.text,
            nickname = nickNameField.text,
            roomDefault = roomDefaultField.text,
        )
        Gadgets.saveConfig(config)

        val filePath = filePathField.text
        val socket: Socket
        try {
            val url = URI(config.dirServer!!)
            socket = Socket(url.host, url.port)
        } catch (_: Exception) {
            val alert = Alert(Alert.AlertType.ERROR)
            alert.title = "Error de conexión"
            alert.headerText = "No se pudo conectar con el servidor"
            alert.contentText = "Comprueba que la dirección del servidor sea correcta y que esté en funcionamiento."
            alert.showAndWait()
            return
        }

        val loader = FXMLLoader(SharePlayController::class.java.getResource("/frontend/roomSharePlay.fxml"))
        val scene = Scene(loader.load())
        val controller = loader.getController<RoomSharePlayController>()
        controller.initData(socket, filePath)
        val stage = Stage()
        stage.scene = scene
        stage.isResizable = false
        stage.title = "SharePlay"
        stage.show()

        (onBrowserFile.scene.window as Stage).close()
    }


    /**
     * Valida que todos los campos obligatorios del formulario tengan valores no vacíos.
     *
     * Acciones que realiza:
     * - Elimina estilos de error previos en los campos requeridos.
     * - Comprueba si cada campo obligatorio (servidor, nickname, sala) está vacío o en blanco.
     * - Si un campo está vacío:
     *     - Se añade un estilo CSS "error" para resaltarlo visualmente.
     *     - Se actualiza su `promptText` para mostrar "Campo requerido".
     *
     * No se muestran alertas emergentes, sino que se proporciona retroalimentación visual directa
     * en los propios campos del formulario.
     *
     * @return `true` si todos los campos obligatorios están correctamente rellenados,
     *         `false` si falta alguno.
     */
    private fun validateRequiredFields(): Boolean {
        var allValid = true

        serverPathComboBox.styleClass.remove("error")
        nickNameField.styleClass.remove("error")
        roomDefaultField.styleClass.remove("error")

        if (serverPathComboBox.editor.text.isNullOrBlank()) {
            serverPathComboBox.promptText = "Campo requerido"
            serverPathComboBox.styleClass.add("error")
            allValid = false
        }

        if (nickNameField.text.isNullOrBlank()) {
            nickNameField.promptText = "Campo requerido"
            nickNameField.styleClass.add("error")
            allValid = false
        }

        if (roomDefaultField.text.isNullOrBlank()) {
            roomDefaultField.promptText = "Campo requerido"
            roomDefaultField.styleClass.add("error")
            allValid = false
        }

        return allValid
    }

}

package tfg.proto.shareplay.frontend

import java.io.File
import java.io.FileOutputStream
import java.net.Socket
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

class RoomSharePlayController {
    lateinit var labelTitle: Label
    lateinit var onCopyConfig: Button
    lateinit var onClose: Button
    lateinit var listRoomInfo: ListView<String>
    lateinit var socket: Socket
    private var playerClient: PlayerClient? = null
    private val roomInfoItems: ObservableList<String> = FXCollections.observableArrayList()

    fun initData(socket: Socket) {
        this.socket = socket
        val config = Gadgets.loadConfig()
        val roomName = config?.roomDefault ?: "Desconocida"
        labelTitle.text = "Sala $roomName"
        val mpvPath = extractAndRunMPV()
        val mpv = MPV(mpvPath)
        playerClient = PlayerClient(socket, config?.roomDefault ?: "", config?.nickname ?: "", mpv)
        @Suppress("UNCHECKED_CAST")
        listRoomInfo.items = roomInfoItems
        startRoomInfoUpdater()
    }

    fun onCopyConfig() {
        val config = Gadgets.loadConfig()
        if (config != null) {
            val textToCopy = "${config.dirServer}|${config.roomDefault}"
            val clipboard = Clipboard.getSystemClipboard()
            val content = ClipboardContent()
            content.putString(textToCopy)
            clipboard.setContent(content)
        }
    }

    fun onClose() {
        val loader = FXMLLoader(javaClass.getResource("/frontend/sharePlay.fxml"))
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

    fun extractAndRunMPV(): String {
        val mpvResource = this::class.java.getResourceAsStream("/mpv/mpv.exe")
            ?: throw IllegalStateException("No se encontró mpv.exe en los recursos")

        val tempFile = File.createTempFile("mpv", ".exe")
        tempFile.deleteOnExit()

        mpvResource.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile.absolutePath
    }

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

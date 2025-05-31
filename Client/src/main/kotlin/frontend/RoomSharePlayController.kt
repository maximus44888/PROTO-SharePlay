package tfg.proto.shareplay.frontend

import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.stage.Stage

class RoomSharePlayController {

    lateinit var labelTitle: Label
    lateinit var onCopyConfig: Button
    lateinit var OnClose: Button
    lateinit var listRoomInfo: ListView<Any>

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

    fun OnClose() {
        val loader = FXMLLoader(javaClass.getResource("/sharePlay.fxml"))
        val root: Parent = loader.load()
        val stage = Stage()
        stage.title = "SharePlay"
        stage.isResizable = false
        stage.scene = Scene(root)
        stage.show()

        (OnClose.scene.window as Stage).close()
    }
}
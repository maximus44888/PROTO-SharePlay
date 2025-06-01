package tfg.proto.shareplay.frontend

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

class SharePlayApplication : Application() {
    override fun start(stage: Stage) {
        val fxmlToLoad = "/frontend/sharePlay.fxml"
        val fxmlLoader = FXMLLoader(SharePlayApplication::class.java.getResource(fxmlToLoad))
        val scene = Scene(fxmlLoader.load(), 700.0, 350.0)
        stage.title = "SharePlay"
        stage.scene = scene
        stage.isResizable = false
        stage.show()
    }
}

fun main() {
    Application.launch(SharePlayApplication::class.java)
}

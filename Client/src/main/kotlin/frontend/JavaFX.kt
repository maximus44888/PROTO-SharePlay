package tfg.proto.shareplay.frontend

import java.net.URL
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.util.Callback

object JavaFX {
    fun buildWindow(location: URL?, stage: Stage = Stage(), controller: (() -> Any)? = null): Stage {
        val loader = FXMLLoader(location)
        controller?.let {
            loader.controllerFactory = Callback { controllerClass ->
                return@Callback controller()
            }
        }

        return stage.apply { scene = Scene(loader.load()) }
    }
}

package tfg.proto.shareplay.frontend

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

/**
 * Clase principal de la aplicación SharePlay que extiende de [Application].
 * Es responsable de inicializar y mostrar la ventana principal de la aplicación.
 */
class ShareplayApplication : Application() {

    /**
     * Método sobrescrito que se ejecuta al iniciar la aplicación.
     * Carga el archivo FXML principal, configura la escena y muestra la ventana.
     *
     * @param stage La ventana principal proporcionada por JavaFX.
     */
    override fun start(stage: Stage) {
        val fxmlLoader = FXMLLoader(javaClass.getResource("/frontend/main.fxml"))
        val scene = Scene(fxmlLoader.load(), 700.0, 350.0)

        stage.title = "SharePlay"
        stage.scene = scene
        stage.isResizable = false
        stage.show()
    }
}

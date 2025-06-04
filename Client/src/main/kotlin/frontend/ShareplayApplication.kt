package tfg.proto.shareplay.frontend

import javafx.application.Application
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
        JavaFX.buildWindow(javaClass.getResource("/frontend/main.fxml"), stage)
            .apply {
                title = "SharePlay"
                isResizable = false
            }.show()
    }
}

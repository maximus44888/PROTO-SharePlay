package tfg.proto.shareplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.time.toDuration

const val mediaURL =
    """https://youtu.be/dQw4w9WgXcQ?si=HD7Uz2iicApUBFUf"""
const val mediaPath =
    """C:\Users\jmaxi\Mis ficheros\Anime\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\S01E01-Undertaker [2F703024].mkv"""
const val mpvPath = "mpv"

suspend fun main() {
    val players = List(4) { MPV(mpvPath) }

    players.forEach { player -> player.loadMedia(Player.buildURI(mediaURL)!!) }

    coroutineScope {
        launch(Dispatchers.IO) {
            // "React" to other players' commands (simulated by reading from console input. In reality, this would be a network message with a custom protocol)
            while (true) {
                val input = readln()
                val doubleInput = input.toDoubleOrNull()
                if (doubleInput != null) players.forEach { player -> player.seek(doubleInput.toDuration(player.durationUnit)) }
                else {
                    val stringInput = input
                    when (stringInput) {
                        "pause" -> players.forEach { player -> player.pause() }
                        "resume" -> players.forEach { player -> player.resume() }
                        "exit" -> return@launch
                    }
                }
            }
        }

        suspend fun <T> SharedFlow<T>.observeAndExecuteOnOthers(player: Player, action: suspend Player.(T) -> Unit) {
            this.collect { data ->
                players
                    .filter { it != player }
                    .forEach { other -> other.action(data) }
            }
        }

        players.forEach { player ->
            launch(Dispatchers.IO) {
                player.pauseEvents.observeAndExecuteOnOthers(player) { if (it) pause() else resume() }
            }

            launch(Dispatchers.IO) {
                player.seekEvents.observeAndExecuteOnOthers(player) { seek(it) }
            }

            launch(Dispatchers.IO) {
                player.loadedMediaEvents.observeAndExecuteOnOthers(player) { loadMedia(it) }
            }
        }
    }
}

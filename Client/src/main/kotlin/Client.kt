package tfg.proto.shareplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.toDuration

const val mediaURL =
    """https://youtu.be/dQw4w9WgXcQ?si=HD7Uz2iicApUBFUf"""
const val mediaPath =
    """C:\Users\jmaxi\Mis ficheros\Anime\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\S01E01-Undertaker [2F703024].mkv"""
const val mpvPath = "mpv"

suspend fun main() {
    val player: Player = MPV(mpvPath)

    player.loadMedia(Player.buildURI(mediaPath)!!)

    coroutineScope {
        launch(Dispatchers.IO) {
            // "React" to other players' commands (simulated by reading from console input. In reality, this would be a network message with a custom protocol)
            while (true) {
                val input = readln()
                val doubleInput = input.toDoubleOrNull()
                if (doubleInput != null) player.seek(doubleInput.toDuration(player.durationUnit))
                else {
                    val stringInput = input
                    when (stringInput) {
                        "pause" -> player.pause()
                        "resume" -> player.resume()
                        "exit" -> return@launch
                    }
                }
            }
        }

        launch(Dispatchers.IO) {
            player.pauseEvents.collect {
                // "Notify" the other players (simulated by printing to console. In reality, this would be a network message with a custom protocol)
                if (it) println("Paused")
                else println("Resumed")
            }
        }

        launch(Dispatchers.IO) {
            player.seekEvents.collect {
                // "Notify" the other players (simulated by printing to console. In reality, this would be a network message with a custom protocol)
                println("Seeked to -> $it")
            }
        }

        launch(Dispatchers.IO) {
            player.loadedMediaEvents.collect {
                // "Notify" the other players (simulated by printing to console. In reality, this would be a network message with a custom protocol)
                println("Loaded file: $it")
            }
        }
    }
}

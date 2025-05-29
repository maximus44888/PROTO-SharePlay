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

        players.forEach { player ->
            launch(Dispatchers.IO) {
                player.pauseEvents.collect {
                    players.forEach { other ->
                        if (other != player) {
                            if (it) other.pause()
                            else other.resume()
                        }
                    }
                }
            }

            launch(Dispatchers.IO) {
                player.seekEvents.collect {
                    players.forEach { other ->
                        if (other != player) {
                            other.seek(it)
                        }
                    }
                }
            }

            launch(Dispatchers.IO) {
                player.loadedMediaEvents.collect {
                    players.forEach { other ->
                        if (other != player) {
                            other.loadMedia(it)
                        }
                    }
                }
            }
        }
    }
}

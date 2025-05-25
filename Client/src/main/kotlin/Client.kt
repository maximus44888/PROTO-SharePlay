package tfg.proto.shareplay

import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.io.path.Path
import kotlin.time.toDuration

const val mediaURL =
    """https://youtu.be/dQw4w9WgXcQ?si=HD7Uz2iicApUBFUf"""
const val mediaPath =
    """C:\Users\jmaxi\Mis ficheros\Anime\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\S01E01-Undertaker [2F703024].mkv"""
const val mpvPath = "mpv"

suspend fun main() {
    val player: Player = MPV(mpvPath)

    println("Loading media from URL: $mediaURL")
    player.loadMedia(URI(mediaURL))
    println("Media loaded!!!")

    println("----------------------------------")

    coroutineScope {
        launch(Dispatchers.IO) {
            player.pauseEvents.collect {
                if (it) println("Paused")
                else println("Resumed")
            }
        }

        launch(Dispatchers.IO) {
            println("Enter to load media from path: $mediaPath")
            readln()
            player.loadMedia(Path(mediaPath).toUri())
            player.pause()
            while (true) {
                println("Enter a time in milliseconds to seek; or 'pause', 'resume' or 'exit'")
                val input = readln()
                val doubleInput = input.toDoubleOrNull()
                if (doubleInput != null) player.seek(doubleInput.toDuration(player.durationUnit))
                else {
                    val stringInput = input
                    when (stringInput) {
                        "pause" -> player.pause()
                        "resume" -> player.resume()
                        "exit" -> {
                            println("Exiting...")
                            return@launch
                        }

                        else -> println("Invalid input")
                    }
                }
            }
        }

        launch(Dispatchers.IO) {
            player.seekEvents.collect {
                println("Seeked to -> $it")
            }
        }

        launch(Dispatchers.IO) {
            player.loadedMediaEvents.collect {
                println("Loaded file: $it")
            }
        }
    }
}

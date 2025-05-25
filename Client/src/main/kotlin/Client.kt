package tfg.proto.shareplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val mediaPath =
    """C:\Users\jmaxi\Mis ficheros\Anime\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\S01E01-Undertaker [2F703024].mkv"""
const val mpvPath = "mpv"

suspend fun main() {
    val player: Player = MPV(mpvPath)

    player.pause(true)
    player.loadFile(mediaPath)

    coroutineScope {
        launch(Dispatchers.IO) {
            player.pauseEvents.collect {
                if (it) println("Paused")
                else println("Resumed")
            }
        }

        launch(Dispatchers.IO) {
            while (true) {
                val input = readln()
                val doubleInput = input.toDoubleOrNull()
                if (doubleInput != null) player.seek(doubleInput)
                else {
                    val stringInput = input
                    when (stringInput) {
                        "pause" -> player.pause(true)
                        "resume" -> player.pause(false)
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
    }
}

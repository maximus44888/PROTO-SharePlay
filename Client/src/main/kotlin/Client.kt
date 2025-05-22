package tfg.proto.shareplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val mediaPath =
    """C:\Users\jmaxi\Mis ficheros\Anime\86 S01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\86 S01P01+SP 1080p Dual Audio BDRip 10 bits DD+ x265-EMBER\S01E01-Undertaker [2F703024].mkv"""
const val mpvPath = "mpv"

suspend fun main() {
    val mpv = MPV(mpvPath)

    mpv.pause(true)
    mpv.loadFile(mediaPath)

    coroutineScope {
        launch(Dispatchers.IO) {
            mpv.observePause().consumeEach {
                println("Pause: $it")
            }
        }

        launch(Dispatchers.IO) {
            while (true) {
                val input = readln().toInt()
                if (input == 0) mpv.pause(false)
                else if (input == 1) mpv.pause(true)
            }
        }
    }
}

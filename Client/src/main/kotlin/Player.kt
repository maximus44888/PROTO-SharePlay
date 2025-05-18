package tfg.proto.shareplay

interface Player {
    suspend fun loadFile(fileIdentifier: String);

    suspend fun pause();
    suspend fun play();

    suspend fun jumpTo(time: Int);
}

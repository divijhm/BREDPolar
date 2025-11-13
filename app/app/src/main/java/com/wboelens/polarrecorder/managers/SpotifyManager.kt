package com.wboelens.polarrecorder.managers

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.SpotifyTrackData

class SpotifyManager(
    private val context: Context,
    private val dataSavers: DataSavers,
) {
    companion object {
        private const val TAG = "SpotifyManager"
        private const val DEVICE_ID = "spotify"
        private const val DATA_TYPE = "track_info"
    }

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var lastLoggedTrackId: String? = null
    private var lastLoggedIsPaused: Boolean? = null
    private var currentRecordingName: String? = null
    private var isSubscribed = false

    fun setSpotifyAppRemote(appRemote: SpotifyAppRemote?) {
        spotifyAppRemote = appRemote
        if (appRemote != null && !isSubscribed) {
            subscribeToPlayerState()
        }
    }

    fun startTracking(recordingName: String) {
        currentRecordingName = recordingName
        Log.d(TAG, "Started tracking Spotify for recording: $recordingName")

        // Subscribe to player state if connected
        spotifyAppRemote?.let {
            subscribeToPlayerState()
        }
    }

    fun stopTracking() {
        currentRecordingName = null
        lastLoggedTrackId = null
        lastLoggedIsPaused = null
        Log.d(TAG, "Stopped tracking Spotify")
    }

    private fun subscribeToPlayerState() {
        if (isSubscribed) return

        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState: PlayerState ->
            handlePlayerStateChange(playerState)
        }?.setErrorCallback { throwable ->
            Log.e(TAG, "Failed to subscribe to player state: ${throwable.message}", throwable)
        }

        isSubscribed = true
        Log.d(TAG, "Subscribed to Spotify player state")
    }

    private fun handlePlayerStateChange(playerState: PlayerState) {
        val track: Track = playerState.track
        val trackId = track.uri
        val isPaused = playerState.isPaused
        val playbackPosition = playerState.playbackPosition

        // Log every state change (track change or play/pause change)
        val shouldLog = trackId != lastLoggedTrackId || isPaused != lastLoggedIsPaused

        if (shouldLog) {
            lastLoggedTrackId = trackId
            lastLoggedIsPaused = isPaused

            val trackData = SpotifyTrackData(
                trackId = trackId,
                trackName = track.name,
                artistName = track.artist.name,
                albumName = track.album.name,
                durationMs = track.duration,
                isPaused = isPaused,
                playbackPosition = playbackPosition,
                playbackSpeed = playerState.playbackSpeed,
                timestamp = System.currentTimeMillis()
            )

            Log.d(TAG, "Track: ${track.name} by ${track.artist.name}, " +
                    "isPaused: $isPaused, position: $playbackPosition, " +
                    "trackId: $trackId")

            // Log to all enabled data savers
            val recordingName = currentRecordingName ?: "no_recording"
            logTrackData(recordingName, trackData)
        }
    }

    private fun logTrackData(recordingName: String, trackData: SpotifyTrackData) {
        val timestamp = System.currentTimeMillis()

        // Log to Spotify data saver (for display purposes)
        dataSavers.spotify.logTrackData(recordingName, trackData)

        // Save to all enabled data savers (FileSystem, MQTT, etc.)
        dataSavers.asList().forEach { saver ->
            if (saver.isEnabled.value) {
                saver.saveData(
                    phoneTimestamp = timestamp,
                    deviceId = DEVICE_ID,
                    recordingName = recordingName,
                    dataType = DATA_TYPE,
                    data = trackData
                )
            }
        }
    }

    fun cleanup() {
        isSubscribed = false
        spotifyAppRemote = null
        currentRecordingName = null
        lastLoggedTrackId = null
        lastLoggedIsPaused = null
        Log.d(TAG, "SpotifyManager cleaned up")
    }
}


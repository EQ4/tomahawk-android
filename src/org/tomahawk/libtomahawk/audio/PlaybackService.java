/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2012, Christopher Reichert <creichert07@gmail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.libtomahawk.audio;

import java.io.IOException;

import org.tomahawk.libtomahawk.Album;
import org.tomahawk.libtomahawk.Track;
import org.tomahawk.libtomahawk.playlist.Playlist;
import org.tomahawk.tomahawk_android.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

public class PlaybackService extends Service implements Handler.Callback, OnCompletionListener,
        OnErrorListener, OnPreparedListener {

    private static String TAG = PlaybackService.class.getName();

    public static final String BROADCAST_NEWTRACK = "org.tomahawk.libtomahawk.audio.PlaybackService.BROADCAST_NEWTRACK";
    private static boolean mIsRunning = false;

    private static PlaybackService mInstance;
    private static final Object[] mWait = new Object[0];

    private Playlist mCurrentPlaylist;
    private MediaPlayer mMediaPlayer;
    private PowerManager.WakeLock mWakeLock;
    private Looper mLooper;
    private Handler mHandler;

    /**
     * Constructs a new PlaybackService.
     */
    @Override
    public void onCreate() {

        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mLooper = thread.getLooper();
        mHandler = new Handler(mLooper, this);

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnErrorListener(this);

        mInstance = this;
        synchronized (mWait) {
            mWait.notifyAll();
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    /**
     * This method is called when this service is started.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (!intent.hasExtra(PlaybackActivity.PLAYLIST_EXTRA))
            throw new IllegalArgumentException("Must pass track extra to PlaybackService.");

        Playlist playlist = (Playlist) intent.getSerializableExtra(PlaybackActivity.PLAYLIST_EXTRA);

        try {
            setCurrentPlaylist(playlist);
        } catch (IOException e) {
            e.printStackTrace();
        }

        setIsRunning(true);
        return startId;
    }

    /**
     * Called when the service is destroyed.
     */
    @Override
    public void onDestroy() {
        setIsRunning(false);
    }

    /**
     * Start or pause playback.
     */
    public void playPause() {
        if (isPlaying())
            pause();
        else
            start();
    }

    public void start() {
        mWakeLock.acquire();
        mMediaPlayer.start();
    }

    /**
     * Stop playback.
     */
    public void stop() {
        mMediaPlayer.stop();
        mWakeLock.release();
        stopForeground(true);
    }

    /**
     * Pause playback.
     */
    public void pause() {
        mWakeLock.release();
        mMediaPlayer.pause();
    }

    /**
     * Start playing the next Track.
     */
    public void next() {
        Track track = mCurrentPlaylist.getNextTrack();
        if (track != null) {
            try {
                setCurrentTrack(track);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Play the previous track.
     */
    public void previous() {

        Track track = mCurrentPlaylist.getPreviousTrack();

        if (track != null) {
            try {
                setCurrentTrack(track);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

	/**
	 * Get the PlaybackService for the given Context.
	 */
    public static PlaybackService get(Context context) {
        if (mInstance == null) {
            context.startService(new Intent(context, PlaybackService.class));

            while (mInstance == null) {
                try {
                    synchronized (mWait) {
                        mWait.wait();
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }

        return mInstance;
    }

    /**
     * Returns whether there is a valid PlaybackService instance.
     */
    public static boolean hasInstance() {
        return mInstance != null;
    }

    @Override
    public boolean handleMessage(Message msg) {
        return false;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "Error with media player");
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {

        Track track = mCurrentPlaylist.getNextTrack();
        if (track != null) {
            try {
                setCurrentTrack(track);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else
            stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
		Log.d(TAG, "Starting playback.");
        start();
    }

    /**
     * Returns whether this PlaybackService is currently playing media.
     */
    public boolean isPlaying() {
        return mMediaPlayer.isPlaying();
    }

    /**
     * Returns true if the service is running.
     * 
     * @return
     */
    public boolean isRunning() {
        return mIsRunning;
    }

    /**
     * Sets the running state of the PlaybackService.
     * 
     * @param running
     */
    public void setIsRunning(boolean running) {
        mIsRunning = running;
    }

    public Playlist getCurrentPlaylist() {
        return mCurrentPlaylist;
    }

    /**
     * Set the current Playlist to playlist and set the current Track to the
     * Playlist's current Track.
     * 
     * @param playlist
     * @throws IOException
     */
    public void setCurrentPlaylist(Playlist playlist) throws IOException {

        mCurrentPlaylist = playlist;
        setCurrentTrack(mCurrentPlaylist.getCurrentTrack());
    }

    public Track getCurrentTrack() {
        return mCurrentPlaylist.getCurrentTrack();
    }

    /**
     * This method sets the current back and prepares it for playback.
     * 
     * @param mCurrentPlaylist
     * @throws IOException
     */
    private void setCurrentTrack(Track track) throws IOException {

        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(track.getPath());
        mMediaPlayer.prepareAsync();

        sendBroadcast(new Intent(BROADCAST_NEWTRACK));
        createPlayingNotification();
    }

    /**
     * Create an ongoing notification and start this service in the foreground.
     */
    private void createPlayingNotification() {

        Track track = getCurrentTrack();
        Album album = track.getAlbum();

        Notification notification = new Notification(R.drawable.ic_launcher, track.getTitle(),
                System.currentTimeMillis());

        Context context = getApplicationContext();
        CharSequence contentTitle = track.getArtist().getName();
        CharSequence contentText = album.getName();
        Intent notificationIntent = new Intent(this, PlaybackService.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        startForeground(3, notification);
    }
}

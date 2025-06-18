package com.example.wifidirectchat;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.util.Log;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VoiceCallManager {
    private static final String TAG = "VoiceCallManager";

    // OPTIMIZED FOR WIFI DIRECT - Higher quality settings
    private static final int SAMPLE_RATE = 16000; // 16kHz for better quality over WiFi
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_MULTIPLIER = 4; // Larger buffers for WiFi Direct

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private volatile boolean isRecording = false;
    private volatile boolean isPlaying = false;
    private volatile boolean isCallActive = false;

    private MainActivity.ServerClass serverClass;
    private MainActivity.ClientClass clientClass;
    private boolean isHost;

    // WIFI DIRECT OPTIMIZATIONS
    private Thread recordingThread;
    private Thread playbackThread;
    private BlockingQueue<byte[]> audioQueue; // Buffer for smooth playback

    // Advanced audio effects
    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler echoCanceler;
    private AutomaticGainControl gainControl;

    // WiFi Direct specific settings
    private static final int WIFI_DIRECT_CHUNK_SIZE = 320; // Optimized for 16kHz
    private static final int AUDIO_QUEUE_SIZE = 50; // Large queue for WiFi Direct

    public VoiceCallManager(MainActivity.ServerClass serverClass, MainActivity.ClientClass clientClass, boolean isHost) {
        this.serverClass = serverClass;
        this.clientClass = clientClass;
        this.isHost = isHost;
        this.audioQueue = new LinkedBlockingQueue<>(AUDIO_QUEUE_SIZE);
    }

    public void startVoiceCall() {
        if (isCallActive) {
            Log.d(TAG, "Voice call already active");
            return;
        }

        Log.d(TAG, "Starting WiFi Direct voice call");
        isCallActive = true;
        audioQueue.clear();

        startAudioRecording();
        startAudioPlayback();
    }

    private void startAudioRecording() {
        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT) * BUFFER_SIZE_MULTIPLIER;

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_IN,
                    AUDIO_FORMAT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                return;
            }

            enableAdvancedAudioEffects();

            isRecording = true;
            audioRecord.startRecording();

            recordingThread = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                byte[] audioBuffer = new byte[WIFI_DIRECT_CHUNK_SIZE];

                while (isRecording && isCallActive) {
                    int bytesRead = audioRecord.read(audioBuffer, 0, WIFI_DIRECT_CHUNK_SIZE);
                    if (bytesRead > 0 && isCallActive) {
                        // WiFi Direct optimized processing
                        byte[] processedAudio = wifiDirectAudioProcessing(audioBuffer, bytesRead);
                        if (processedAudio != null) {
                            sendAudioDataOptimized(processedAudio);
                        }
                    }
                }
            });
            recordingThread.start();

            Log.d(TAG, "WiFi Direct audio recording started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio recording", e);
        }
    }

    private void startAudioPlayback() {
        try {
            int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT) * BUFFER_SIZE_MULTIPLIER;

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_OUT,
                    AUDIO_FORMAT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
            );

            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed");
                return;
            }

            isPlaying = true;
            audioTrack.play();

            // Dedicated playback thread for smooth audio
            playbackThread = new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

                while (isPlaying && isCallActive) {
                    try {
                        byte[] audioData = audioQueue.take(); // Blocking call
                        if (audioData != null && audioTrack != null && isPlaying) {
                            audioTrack.write(audioData, 0, audioData.length);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Playback error", e);
                    }
                }
            });
            playbackThread.start();

            Log.d(TAG, "WiFi Direct audio playback started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio playback", e);
        }
    }

    private void enableAdvancedAudioEffects() {
        try {
            int audioSessionId = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                audioSessionId = audioRecord.getAudioSessionId();
            }

            // Enable all available audio effects for WiFi Direct
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId);
                    if (noiseSuppressor != null) {
                        noiseSuppressor.setEnabled(true);
                        Log.d(TAG, "Noise Suppressor enabled");
                    }
                }

                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId);
                    if (echoCanceler != null) {
                        echoCanceler.setEnabled(true);
                        Log.d(TAG, "Echo Canceler enabled");
                    }
                }

                if (AutomaticGainControl.isAvailable()) {
                    gainControl = AutomaticGainControl.create(audioSessionId);
                    if (gainControl != null) {
                        gainControl.setEnabled(true);
                        Log.d(TAG, "Automatic Gain Control enabled");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling audio effects", e);
        }
    }

    // WiFi Direct optimized audio processing
    private byte[] wifiDirectAudioProcessing(byte[] audioData, int length) {
        // Convert to shorts for processing
        short[] samples = new short[length / 2];
        ByteBuffer.wrap(audioData, 0, length).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);

        // WiFi Direct specific optimizations
        short[] processed = applyWifiDirectOptimizations(samples);

        // Convert back to bytes
        byte[] result = new byte[processed.length * 2];
        ByteBuffer.wrap(result).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(processed);

        return result;
    }

    private short[] applyWifiDirectOptimizations(short[] samples) {
        short[] result = new short[samples.length];

        // More aggressive processing for WiFi Direct
        for (int i = 0; i < samples.length; i++) {
            short sample = samples[i];

            // Aggressive noise gate (WiFi Direct can handle it)
            if (Math.abs(sample) < 200) {
                sample = 0;
            } else {
                // Amplify valid audio for better transmission
                sample = (short) Math.max(-32767, Math.min(32767, sample * 1.2));
            }

            result[i] = sample;
        }

        return result;
    }

    // Optimized sending for WiFi Direct
    private void sendAudioDataOptimized(byte[] audioData) {
        if (!isCallActive || audioData == null) return;

        try {
            // WiFi Direct can handle larger packets efficiently
            if (isHost && serverClass != null) {
                serverClass.write(new byte[]{3}, 0, 1); // Voice call audio type

                // Send length
                ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
                lengthBuffer.putInt(audioData.length);
                serverClass.write(lengthBuffer.array(), 0, 4);

                // Send audio data in one go (WiFi Direct optimization)
                serverClass.write(audioData, 0, audioData.length);

            } else if (!isHost && clientClass != null) {
                clientClass.write(new byte[]{3}, 0, 1);

                ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
                lengthBuffer.putInt(audioData.length);
                clientClass.write(lengthBuffer.array(), 0, 4);

                clientClass.write(audioData, 0, audioData.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending audio data", e);
        }
    }

    public void playReceivedAudio(byte[] audioData) {
        if (!isCallActive || audioData == null || audioData.length == 0) return;

        try {
            // Add to queue for smooth playback
            if (!audioQueue.offer(audioData)) {
                // Queue full, remove oldest and add new (prevents stuttering)
                audioQueue.poll();
                audioQueue.offer(audioData);
                Log.d(TAG, "Audio queue full, replaced oldest sample");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error queuing received audio", e);
        }
    }

    public void endVoiceCall() {
        if (!isCallActive) {
            Log.d(TAG, "Voice call already ended");
            return;
        }

        Log.d(TAG, "Ending WiFi Direct voice call");
        isCallActive = false;

        stopAudioRecording();
        stopAudioPlayback();
        releaseAudioEffects();

        if (audioQueue != null) {
            audioQueue.clear();
        }
    }

    private void stopAudioRecording() {
        isRecording = false;

        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.d(TAG, "Audio recording stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio recording", e);
            }
        }
    }

    private void stopAudioPlayback() {
        isPlaying = false;

        if (playbackThread != null) {
            try {
                playbackThread.interrupt();
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
                Log.d(TAG, "Audio playback stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio playback", e);
            }
        }
    }

    private void releaseAudioEffects() {
        try {
            if (noiseSuppressor != null) {
                noiseSuppressor.release();
                noiseSuppressor = null;
            }
            if (echoCanceler != null) {
                echoCanceler.release();
                echoCanceler = null;
            }
            if (gainControl != null) {
                gainControl.release();
                gainControl = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing audio effects", e);
        }
    }

    public boolean isCallActive() {
        return isCallActive;
    }
}

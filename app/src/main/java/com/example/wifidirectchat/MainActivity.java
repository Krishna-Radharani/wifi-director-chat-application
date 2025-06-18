package com.example.wifidirectchat;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.HorizontalScrollView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

// ML Kit imports for Smart Reply
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage;
import com.google.firebase.ml.naturallanguage.smartreply.FirebaseSmartReply;
import com.google.firebase.ml.naturallanguage.smartreply.FirebaseTextMessage;
import com.google.firebase.ml.naturallanguage.smartreply.SmartReplySuggestion;
import com.google.firebase.ml.naturallanguage.smartreply.SmartReplySuggestionResult;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.PopupMenu;
import androidx.appcompat.app.AppCompatDelegate;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.emoji2.emojipicker.EmojiPickerView;

import android.os.Environment;



public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String SMART_REPLY_TAG = "SmartReply";

    TextView connectionStatus, messageTextView;
    //Button aSwitch, discoverButton;
    ListView listView;
    EditText typeMsg;
    ImageButton sendButton;
    LinearLayout messageContainer;
    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;
    IntentFilter intentFilter;
    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;
    Socket socket;
    ServerClass serverClass;
    ClientClass clientClass;
    boolean isHost;
    ActivityResultLauncher<Intent> wifiSettingsLauncher;
    LinearLayout replyLayout;
    TextView replyText;
    ImageView closeReply;
    String replyingToMessage = null;
    MediaPlayer mediaPlayerSend;
    MediaPlayer mediaPlayerReceive;
    static final int PICK_FILE_REQUEST_CODE = 1001;
    FileInputStream fileInputStream;
    boolean isSendingFile = false;
    OutputStream socketOutputStream;
    boolean isReceivingFile = false;
    String currentFileName="";
    long currentFileSize=0;
    FileOutputStream fileOutputStream;
    LinearLayout chatLayout;
    // AI Smart Reply variables
    private FirebaseSmartReply smartReply;
    private List<FirebaseTextMessage> conversationHistory;
    private HorizontalScrollView   suggestionsScrollView;
    private LinearLayout suggestionsContainer;
    private Handler suggestionHandler;
    private boolean isSmartReplyInitialized = false;
    // Add these new variables with your existing ones
    private ImageView overflowMenu;
    private ImageView wifiToggleIcon;
    private ImageView voiceSendButton;
    private ImageView emojiIcon;
    private CardView deviceListCard;
    private boolean isRecording = false;
    // Theme management variables
    private static final String PREFS_NAME = "theme_prefs";
    private static final String THEME_KEY = "selected_theme";
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    // Simple voice recording variables
    private MediaRecorder mediaRecorder;
    private String audioFileName;
    private MediaPlayer mediaPlayer;
    private EmojiPickerView emojiPickerView;
    private boolean isEmojiPickerVisible = false;
    private VoiceCallManager voiceCallManager;
    private ImageButton voiceCallButton;
    private LinearLayout callControlLayout;
    private ImageButton endCallButton;
    private TextView callStatusText;
    private boolean isInVoiceCall = false;
    private static final int RECORD_AUDIO_PERMISSION_REQUEST = 200;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme BEFORE calling super.onCreate()
        int savedTheme = getThemePreference();
        applyTheme(savedTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Starting MainActivity");

        chatLayout = findViewById(R.id.chatLayout);

        // FIXED: Call initialwork() FIRST to initialize typeMsg
        initialwork();  // This must come first
        initializeSmartReply();

        // NOW call WhatsApp UI initialization (which uses typeMsg)
        initializeWhatsAppUI();  // This calls setupVoiceSendButton() which needs typeMsg

        // Add this in onCreate() after other initializations
        checkAudioPermission();


        ImageView sendFileButton = findViewById(R.id.button_send_file);
        sendFileButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
        });

        try {
            mediaPlayerSend = MediaPlayer.create(this, R.raw.send_sound);
            mediaPlayerReceive = MediaPlayer.create(this, R.raw.receive_sound);
        } catch (Exception e) {
            Log.w(TAG, "Sound files not found, continuing without sound effects");
        }

        Button clearChatButton = findViewById(R.id.clearChatButton);
        messageContainer = findViewById(R.id.messageContainer);

        clearChatButton.setOnClickListener(v -> {
            Log.d(TAG, "Clearing chat and conversation history");
            messageContainer.removeAllViews();
            if (conversationHistory != null) {
                conversationHistory.clear();
                Log.d(SMART_REPLY_TAG, "Conversation history cleared");
            }
            hideSuggestions();
        });

        exqListener();

        replyLayout = findViewById(R.id.replyLayout);
        replyText = findViewById(R.id.replyText);
        closeReply = findViewById(R.id.closeReply);

        closeReply.setOnClickListener(v -> {
            replyingToMessage = null;
            replyLayout.setVisibility(View.GONE);
        });
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }



// Add these new methods to your MainActivity class:

    // Initialize WhatsApp-style UI components
    private void initializeWhatsAppUI() {
        overflowMenu = findViewById(R.id.overflowMenu);
        wifiToggleIcon = findViewById(R.id.wifiToggleIcon);
        voiceSendButton = findViewById(R.id.voiceSendButton);
        emojiIcon = findViewById(R.id.emojiIcon);
        deviceListCard = findViewById(R.id.deviceListCard);
        emojiPickerView = findViewById(R.id.emojiPickerView);
        // Initialize voice call UI
        voiceCallButton = findViewById(R.id.voiceCallButton);
        callControlLayout = findViewById(R.id.callControlLayout);
        endCallButton = findViewById(R.id.endCallButton);
        callStatusText = findViewById(R.id.callStatusText);
        setupEmojiPicker();

        // Overflow menu click
        overflowMenu.setOnClickListener(v -> showOverflowMenu(v));

        // WiFi toggle click
        wifiToggleIcon.setOnClickListener(v -> toggleWifi());

        // Voice/Send button functionality
        setupVoiceSendButton();

        // Emoji click (placeholder)
        emojiIcon.setOnClickListener(v -> toggleEmojiPicker());

        // Set up voice call button
        voiceCallButton.setOnClickListener(v -> initiateVoiceCall());
        endCallButton.setOnClickListener(v -> endVoiceCall());
    }
    private void initiateVoiceCall() {
        if (!isInVoiceCall) {
            // Check audio permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        RECORD_AUDIO_PERMISSION_REQUEST);
                return;
            }

            // Send voice call request
            sendMessage("VOICE_CALL_REQUEST");
            startVoiceCall();
        }
    }


    private void configureAudioSession() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            // WiFi Direct optimized audio settings
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

            // Disable speakerphone for better quality
            audioManager.setSpeakerphoneOn(false);

            // Set optimal volume for WiFi Direct
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (int)(maxVolume * 0.9), 0);

            // Request exclusive audio focus
            audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            );

            // WiFi Direct specific optimizations
            try {
                // Disable audio processing that might interfere
                audioManager.setParameters("noise_suppression=off"); // We handle this ourselves
                audioManager.setParameters("echo_cancellation=off"); // We handle this ourselves
            } catch (Exception e) {
                Log.w(TAG, "Could not set audio parameters: " + e.getMessage());
            }
        }
    }




    private void startVoiceCall() {
        configureAudioSession();
        isInVoiceCall = true;
        voiceCallButton.setVisibility(View.GONE);
        callControlLayout.setVisibility(View.VISIBLE);

        // Initialize voice call manager if not already done
        if (voiceCallManager == null) {
            voiceCallManager = new VoiceCallManager(serverClass, clientClass, isHost);
        }

        voiceCallManager.startVoiceCall();

        Toast.makeText(this, "Voice call started", Toast.LENGTH_SHORT).show();
    }

    private void endVoiceCall() {
        if (!isInVoiceCall) {
            return; // Already ended
        }

        isInVoiceCall = false;
        voiceCallButton.setVisibility(View.VISIBLE);
        callControlLayout.setVisibility(View.GONE);

        if (voiceCallManager != null) {
            voiceCallManager.endVoiceCall();
        }

        // Only send end call message once
        if (isHost && serverClass != null || !isHost && clientClass != null) {
            sendMessage("VOICE_CALL_END");
        }

        Toast.makeText(this, "Voice call ended", Toast.LENGTH_SHORT).show();
    }

    private void showIncomingCallDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Incoming Voice Call")
                .setMessage("Accept voice call?")
                .setPositiveButton("Accept", (dialog, which) -> startVoiceCall())
                .setNegativeButton("Decline", (dialog, which) -> sendMessage("VOICE_CALL_DECLINED"))
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initiateVoiceCall();
            } else {
                Toast.makeText(this, "Audio permission required for voice calls", Toast.LENGTH_SHORT).show();
            }
        }
    }





    private void setupEmojiPicker() {
        if (emojiPickerView != null) {
            // Set initial theme based on current app theme
            int currentTheme = getThemePreference();
            if (currentTheme == THEME_DARK) {
                emojiPickerView.setBackgroundColor(Color.parseColor("#121212"));
            } else {
                emojiPickerView.setBackgroundColor(Color.parseColor("#FFFFFF"));
            }

            // Set up emoji picker listener
            emojiPickerView.setOnEmojiPickedListener(emoji -> {
                if (typeMsg != null) {
                    int cursorPosition = typeMsg.getSelectionStart();
                    String currentText = typeMsg.getText().toString();
                    String newText = currentText.substring(0, cursorPosition) +
                            emoji.getEmoji() +
                            currentText.substring(cursorPosition);
                    typeMsg.setText(newText);
                    typeMsg.setSelection(cursorPosition + emoji.getEmoji().length());
                }

                hideEmojiPicker();
                Toast.makeText(this, "Emoji added!", Toast.LENGTH_SHORT).show();
            });
        }
    }


    private void toggleEmojiPicker() {
        if (isEmojiPickerVisible) {
            hideEmojiPicker();
        } else {
            showEmojiPicker();
        }
    }

    private void showEmojiPicker() {
        if (emojiPickerView != null) {
            emojiPickerView.setVisibility(View.VISIBLE);
            isEmojiPickerVisible = true;

            // Apply theme-based background
            int currentTheme = getThemePreference();
            if (currentTheme == THEME_DARK) {
                emojiPickerView.setBackgroundColor(Color.parseColor("#2C2C2C"));
            } else {
                emojiPickerView.setBackgroundColor(Color.parseColor("#FFFFFF"));
            }

            // Hide keyboard when showing emoji picker
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }

            // CHANGE EMOJI ICON TO KEYBOARD ICON
            emojiIcon.setImageResource(R.drawable.ic_keyboard);
        }
    }


    private void hideEmojiPicker() {
        if (emojiPickerView != null) {
            emojiPickerView.setVisibility(View.GONE);
            isEmojiPickerVisible = false;

            // Change keyboard icon back to emoji icon
            try {
                emojiIcon.setImageResource(R.drawable.ic_emoji);
            } catch (Exception e) {
                // Keep current icon if emoji icon doesn't exist
            }
        }
    }




    // Overflow menu implementation
    private void showOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());

        // Update menu item text based on current theme
        MenuItem themeItem = popup.getMenu().findItem(R.id.action_toggle_theme);
        int currentTheme = getThemePreference();
        if (currentTheme == THEME_DARK) {
            themeItem.setTitle("Toggle to Light Mode");
        } else {
            themeItem.setTitle("Toggle to Dark Mode");
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_scan_devices) {
                scanForDevices();
                return true;
            } else if (id == R.id.action_toggle_theme) {
                toggleTheme();
                return true;
            }
            return false;
        });

        popup.show();
    }


    // Scan for devices functionality
    private void scanForDevices() {
        deviceListCard.setVisibility(View.VISIBLE);

        // Use your existing discover peers logic
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                connectionStatus.setText("Scanning for devices...");
            }

            @Override
            public void onFailure(int reason) {
                connectionStatus.setText("Scan failed");
                deviceListCard.setVisibility(View.GONE);
            }
        });
    }

    // WiFi toggle functionality
    private void toggleWifi() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        startActivity(intent);
    }

    // Theme toggle functionality - LIGHT/DARK ONLY with proper button text
    private void toggleTheme() {
        int currentTheme = getThemePreference();
        int newTheme;
        String themeName;

        // Toggle between ONLY Light and Dark (no System Default)
        if (currentTheme == THEME_DARK) {
            newTheme = THEME_LIGHT;
            themeName = "Light Mode";
        } else {
            // Default to Dark if currently Light or System
            newTheme = THEME_DARK;
            themeName = "Dark Mode";
        }

        // Save and apply new theme
        saveThemePreference(newTheme);
        applyTheme(newTheme);

        Toast.makeText(this, "Switched to " + themeName, Toast.LENGTH_SHORT).show();

        // Recreate activity to apply theme changes
        recreate();
    }



    // Update your setupVoiceSendButton method to handle emoji picker
    private void setupVoiceSendButton() {
        if (typeMsg == null) {
            Log.e(TAG, "ERROR: typeMsg is null in setupVoiceSendButton()");
            return;
        }

        // Hide emoji picker when user starts typing
        typeMsg.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && isEmojiPickerVisible) {
                hideEmojiPicker();
            }
        });

        typeMsg.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    voiceSendButton.setImageResource(R.drawable.ic_send);
                    voiceSendButton.setOnClickListener(v -> sendMessage());
                } else {
                    voiceSendButton.setImageResource(R.drawable.ic_mic);
                    voiceSendButton.setOnClickListener(v -> toggleVoiceRecording());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Initial setup - set to mic icon by default
        voiceSendButton.setImageResource(R.drawable.ic_mic);
        voiceSendButton.setOnClickListener(v -> toggleVoiceRecording());
    }

    //for voice call hanlding
    private void sendMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        // Don't add voice call control messages to chat UI
        if (!message.equals("VOICE_CALL_REQUEST") &&
                !message.equals("VOICE_CALL_END") &&
                !message.equals("VOICE_CALL_DECLINED")) {
            appendMessageWithMeta(message, true);
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                byte[] messageBytes = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    messageBytes = message.getBytes(StandardCharsets.UTF_8);
                }

                if (isHost) {
                    serverClass.write(new byte[]{0}, 0, 1);
                } else {
                    clientClass.write(new byte[]{0}, 0, 1);
                }

                ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
                lengthBuffer.putInt(messageBytes.length);

                if (isHost) {
                    serverClass.write(lengthBuffer.array(), 0, 4);
                    serverClass.write(messageBytes, 0, messageBytes.length);
                } else {
                    clientClass.write(lengthBuffer.array(), 0, 4);
                    clientClass.write(messageBytes, 0, messageBytes.length);
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Error sending message", Toast.LENGTH_SHORT).show());
            }
        });
    }



    // Send message functionality (use your existing logic)
    private void sendMessage() {
        String msg = typeMsg.getText().toString().trim();
        if (!msg.isEmpty()) {
            // CLEAR THE INPUT IMMEDIATELY AFTER GETTING THE TEXT
            typeMsg.setText("");  // Move this line here

            // Hide AI suggestions when sending
            hideSuggestions();

            if (replyingToMessage != null) {
                msg = "Replying to: " + replyingToMessage + "\n" + msg;
                replyingToMessage = null;
                replyLayout.setVisibility(View.GONE);
            }

            appendMessageWithMeta(msg, true);

            if(mediaPlayerSend != null){
                mediaPlayerSend.start();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            String finalMsg = msg;
            executor.execute(() -> {
                try {
                    @SuppressLint({"NewApi", "LocalSuppress"}) byte[] messageBytes = finalMsg.getBytes(StandardCharsets.UTF_8);

                    if (isHost) {
                        serverClass.write(new byte[]{0}, 0, 1);
                    } else {
                        clientClass.write(new byte[]{0}, 0, 1);
                    }

                    ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
                    lengthBuffer.putInt(messageBytes.length);

                    if (isHost) {
                        serverClass.write(lengthBuffer.array(), 0, 4);
                        serverClass.write(messageBytes, 0, messageBytes.length);
                    } else {
                        clientClass.write(lengthBuffer.array(), 0, 4);
                        clientClass.write(messageBytes, 0, messageBytes.length);
                    }

                    // REMOVE THIS LINE - we already cleared it above
                    // runOnUiThread(() -> typeMsg.setText(""));

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Error sending message", Toast.LENGTH_SHORT).show());
                }
            });
        }
    }


    // Voice recording functionality (placeholder)
    private void toggleVoiceRecording() {
        if (!isRecording) {
            startDirectRecording();
        } else {
            stopAndSendRecording();
        }
    }
    private void startDirectRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }

        try {
            File cacheDir = getExternalCacheDir();
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdirs()) {
                    throw new IOException("Failed to create directory");
                }
            }

            audioFileName = cacheDir.getAbsolutePath() +
                    "/voice_" + System.currentTimeMillis() + ".3gp";

            // Release any existing recorder
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(audioFileName);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setAudioSamplingRate(8000);
            mediaRecorder.setAudioEncodingBitRate(12200);

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            runOnUiThread(() -> {
                voiceSendButton.setImageResource(R.drawable.ic_stop);
                voiceSendButton.setBackgroundColor(Color.RED);
                Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show();
            });

        } catch (Exception e) {
            Log.e(TAG, "Recording failed: " + e.getMessage());
            resetRecordingState();
            runOnUiThread(() ->
                    Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }




    private void stopAndSendRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording");
            return;
        }

        try {
            Log.d(TAG, "Stopping voice recording...");

            // Stop and release MediaRecorder properly
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            isRecording = false;
            voiceSendButton.setImageResource(R.drawable.ic_mic);
            voiceSendButton.setBackgroundResource(R.drawable.circle_button_background);

            // Wait for file system to sync
            Thread.sleep(500); // Increased wait time

            // STRICT file validation with detailed logging
            File voiceFile = new File(audioFileName);

            Log.d(TAG, "=== VOICE FILE VALIDATION ===");
            Log.d(TAG, "File path: " + audioFileName);
            Log.d(TAG, "File exists: " + voiceFile.exists());
            Log.d(TAG, "File size: " + voiceFile.length() + " bytes");
            Log.d(TAG, "File can read: " + voiceFile.canRead());
            Log.d(TAG, "File last modified: " + new Date(voiceFile.lastModified()));

            if (!voiceFile.exists()) {
                Toast.makeText(this, "Recording failed - file not created", Toast.LENGTH_SHORT).show();
                return;
            }

            if (voiceFile.length() == 0) {
                Toast.makeText(this, "Recording failed - no audio captured", Toast.LENGTH_SHORT).show();
                voiceFile.delete();
                return;
            }

            // INCREASED minimum size - AMR_NB files should be at least 1KB for real audio
            if (voiceFile.length() < 1000) {
                Toast.makeText(this, "Recording too short - please record for at least 2 seconds", Toast.LENGTH_LONG).show();
                voiceFile.delete();
                return;
            }

            // TEST if file actually contains audio by trying to create MediaPlayer
            if (!testAudioFileContent(voiceFile)) {
                Toast.makeText(this, "Recording failed - no valid audio content", Toast.LENGTH_LONG).show();
                voiceFile.delete();
                return;
            }

            Log.d(TAG, "Voice file validation passed: " + voiceFile.length() + " bytes");

            // Send voice message directly
            sendVoiceMessageDirectly();

        } catch (RuntimeException e) {
            Log.e(TAG, "MediaRecorder stop failed - no valid audio data: " + e.getMessage());
            Toast.makeText(this, "Recording failed - please try again", Toast.LENGTH_LONG).show();

            // Clean up
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.release();
                } catch (Exception ex) {
                    // Ignore
                }
                mediaRecorder = null;
            }

            // Delete invalid file
            try {
                File invalidFile = new File(audioFileName);
                if (invalidFile.exists()) {
                    invalidFile.delete();
                }
            } catch (Exception ex) {
                Log.w(TAG, "Could not delete invalid file: " + ex.getMessage());
            }

            isRecording = false;
            voiceSendButton.setImageResource(R.drawable.ic_mic);
            voiceSendButton.setBackgroundResource(R.drawable.circle_button_background);

        } catch (Exception e) {
            Log.e(TAG, "Failed to stop recording: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Failed to send voice message: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // Clean up on error
            isRecording = false;
            voiceSendButton.setImageResource(R.drawable.ic_mic);
            voiceSendButton.setBackgroundResource(R.drawable.circle_button_background);
        }
    }


    // Replace your existing testAudioFileContent() method with this enhanced version
    private boolean testAudioFileContent(File audioFile) {
        try {
            MediaPlayer testPlayer = new MediaPlayer();
            testPlayer.setDataSource(audioFile.getAbsolutePath());
            testPlayer.prepare();

            int duration = testPlayer.getDuration();
            testPlayer.release();

            Log.d(TAG, "Audio file test - Duration: " + duration + "ms, Size: " + audioFile.length() + " bytes");

            // ENHANCED validation with stricter requirements
            if (duration > 500 && audioFile.length() > 2000) {
                Log.d(TAG, "Audio file validation PASSED");
                return true;
            } else {
                Log.e(TAG, "Audio file validation FAILED - Duration too short or file too small");
                return false;
            }

        } catch (Exception e) {
            Log.e(TAG, "Audio file validation FAILED - Cannot create MediaPlayer: " + e.getMessage());
            return false;
        }
    }

    // Add this method right after your existing testAudioFileContent() method
    private boolean validateAudioFile(File audioFile) {
        try {
            MediaPlayer testPlayer = new MediaPlayer();
            testPlayer.setDataSource(audioFile.getAbsolutePath());
            testPlayer.prepare();

            int duration = testPlayer.getDuration();
            testPlayer.release();

            // Ensure minimum duration and file size
            return duration > 500 && audioFile.length() > 2000;
        } catch (Exception e) {
            return false;
        }
    }





    private void testRecordedAudio() {
        try {
            File testFile = new File(audioFileName);
            if (testFile.exists() && testFile.length() > 0) {
                MediaPlayer testPlayer = new MediaPlayer();
                testPlayer.setDataSource(audioFileName);
                testPlayer.prepare();

                int duration = testPlayer.getDuration();
                testPlayer.release();

                Log.d(TAG, "Recorded audio duration: " + duration + "ms");
                Log.d(TAG, "File size: " + testFile.length() + " bytes");

                if (duration < 100) {
                    Log.w(TAG, "Very short recording detected");
                }
            } else {
                Log.e(TAG, "Test file does not exist or is empty");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to test recorded audio: " + e.getMessage());
        }
    }



    private void sendVoiceMessageDirectly() {
        try {
            // Create proper voice message bubble for sender (with play button)
            createSentVoiceMessage(audioFileName);

            // Play send sound effect
            if (mediaPlayerSend != null) {
                mediaPlayerSend.start();
            }

            // Send the actual audio file through WiFi Direct
            sendVoiceFileOverWifiDirect();

            Toast.makeText(this, "Voice message sent!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error in sendVoiceMessageDirectly: " + e.getMessage());
            Toast.makeText(this, "Failed to send voice message", Toast.LENGTH_SHORT).show();
        }
    }



    private void createSentVoiceMessage(String audioFilePath) {
        runOnUiThread(() -> {
            String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

            FrameLayout wrapper = new FrameLayout(this);
            LinearLayout bubbleLayout = new LinearLayout(this);
            bubbleLayout.setOrientation(LinearLayout.VERTICAL); // CHANGED TO VERTICAL

            try {
                bubbleLayout.setBackgroundResource(R.drawable.bubble_sender);
            } catch (Exception e) {
                bubbleLayout.setBackgroundColor(Color.LTGRAY);
            }

            bubbleLayout.setPadding(20, 14, 20, 14);

            // TOP ROW: Play button and Voice message text (horizontal)
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            // Play button
            ImageView playButton = new ImageView(this);
            playButton.setImageResource(android.R.drawable.ic_media_play);
            playButton.setLayoutParams(new LinearLayout.LayoutParams(96, 96));

            // Voice message text
            TextView voiceText = new TextView(this);
            voiceText.setText("Voice message");
            voiceText.setTextColor(Color.BLACK);
            voiceText.setTextSize(16f);
            voiceText.setPadding(12, 0, 0, 0);

            // Add to top row
            topRow.addView(playButton);
            topRow.addView(voiceText);

            // BOTTOM ROW: Timestamp (aligned to right)
            TextView timestampText = new TextView(this);
            timestampText.setText(timestamp);
            timestampText.setTextColor(Color.GRAY);
            timestampText.setTextSize(12f);
            timestampText.setGravity(Gravity.END); // Align to right

            LinearLayout.LayoutParams timestampParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            timestampParams.setMargins(0, 8, 0, 0); // Top margin to separate from content
            timestampText.setLayoutParams(timestampParams);

            // Add click listener with icon change functionality
            playButton.setOnClickListener(new View.OnClickListener() {
                MediaPlayer localPlayer = null;
                boolean isPlaying = false;

                @Override
                public void onClick(View v) {
                    if (!isPlaying) {
                        try {
                            if (localPlayer != null) {
                                localPlayer.release();
                            }
                            localPlayer = new MediaPlayer();
                            localPlayer.setDataSource(audioFilePath);
                            localPlayer.prepare();
                            localPlayer.start();
                            playButton.setImageResource(android.R.drawable.ic_media_pause);
                            isPlaying = true;

                            localPlayer.setOnCompletionListener(mp -> {
                                playButton.setImageResource(android.R.drawable.ic_media_play);
                                isPlaying = false;
                                localPlayer.release();
                                localPlayer = null;
                            });
                        } catch (Exception e) {
                            playButton.setImageResource(android.R.drawable.ic_media_play);
                            isPlaying = false;
                            Toast.makeText(MainActivity.this, "Error playing voice message", Toast.LENGTH_SHORT).show();
                            if (localPlayer != null) {
                                localPlayer.release();
                                localPlayer = null;
                            }
                        }
                    } else {
                        if (localPlayer != null) {
                            localPlayer.stop();
                            localPlayer.release();
                            localPlayer = null;
                        }
                        playButton.setImageResource(android.R.drawable.ic_media_play);
                        isPlaying = false;
                    }
                }
            });

            // Add both rows to bubble
            bubbleLayout.addView(topRow);
            bubbleLayout.addView(timestampText);
            wrapper.addView(bubbleLayout);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(10, 10, 10, 10);
            params.gravity = Gravity.END;
            wrapper.setLayoutParams(params);

            messageContainer.addView(wrapper);
            scrollToBottom();
            addToConversationHistory("Voice message", true);
        });
    }




    private void sendVoiceFileOverWifiDirect() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            FileInputStream fileInputStream = null;
            try {
                File voiceFile = new File(audioFileName);

                // Validate file before sending
                if (!voiceFile.exists() || voiceFile.length() < 1000) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Invalid voice recording", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Send message type 2 for voice
                byte[] messageType = new byte[]{2};
                if (isHost) {
                    serverClass.write(messageType, 0, 1);
                } else {
                    clientClass.write(messageType, 0, 1);
                }

                // Send file size
                ByteBuffer sizeBuffer = ByteBuffer.allocate(8);
                sizeBuffer.putLong(voiceFile.length());
                byte[] sizeBytes = sizeBuffer.array();

                if (isHost) {
                    serverClass.write(sizeBytes, 0, 8);
                } else {
                    clientClass.write(sizeBytes, 0, 8);
                }

                // Send file data in chunks
                fileInputStream = new FileInputStream(voiceFile);
                byte[] buffer = new byte[1024];
                int bytesRead;
                long totalSent = 0;

                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    if (isHost) {
                        serverClass.write(buffer, 0, bytesRead);
                    } else {
                        clientClass.write(buffer, 0, bytesRead);
                    }
                    totalSent += bytesRead;
                }

                // REMOVED: Don't create duplicate message here
                // The sender already has their voice message bubble from createSentVoiceMessage()

            } catch (Exception e) {
                Log.e(TAG, "Error sending voice message: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to send voice message", Toast.LENGTH_SHORT).show());
            } finally {
                try {
                    if (fileInputStream != null) fileInputStream.close();
                    // INCREASED delay so sender can play their message
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        new File(audioFileName).delete();
                    }, 120000); // Delete after 2 minutes instead of 30 seconds
                } catch (IOException e) {
                    Log.e(TAG, "Error closing streams", e);
                }
            }
        });
    }








    private void deleteTemporaryVoiceFile() {
        try {
            if (audioFileName != null) {
                File tempFile = new File(audioFileName);
                if (tempFile.exists()) {
                    boolean deleted = tempFile.delete();
                    if (deleted) {
                        Log.d(TAG, "Temporary voice file deleted successfully");
                    } else {
                        Log.w(TAG, "Failed to delete temporary voice file");
                    }
                } else {
                    Log.d(TAG, "Temporary voice file doesn't exist (already deleted?)");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting temporary file: " + e.getMessage());
        }
    }




    // Initialize Smart Reply AI - CORRECTED VERSION
    private void initializeSmartReply() {
        Log.d(SMART_REPLY_TAG, "Starting Smart Reply initialization...");

        try {
            smartReply = FirebaseNaturalLanguage.getInstance().getSmartReply();
            Log.d(SMART_REPLY_TAG, "FirebaseSmartReply instance created successfully");

            conversationHistory = new ArrayList<>();
            Log.d(SMART_REPLY_TAG, "Conversation history list initialized");

            suggestionsScrollView = findViewById(R.id.suggestionsScrollView);
            suggestionsContainer = findViewById(R.id.suggestionsContainer);

            if (suggestionsScrollView == null) {
                Log.e(SMART_REPLY_TAG, "ERROR: suggestionsScrollView not found in layout!");
                Toast.makeText(this, "AI Suggestions layout missing - check XML", Toast.LENGTH_LONG).show();
                return;
            }

            if (suggestionsContainer == null) {
                Log.e(SMART_REPLY_TAG, "ERROR: suggestionsContainer not found in layout!");
                Toast.makeText(this, "AI Suggestions container missing - check XML", Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(SMART_REPLY_TAG, "UI elements found successfully");

            suggestionHandler = new Handler(Looper.getMainLooper());
            Log.d(SMART_REPLY_TAG, "Handler created successfully");

            if (typeMsg != null) {
                typeMsg.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        Log.d(SMART_REPLY_TAG, "Text changed: '" + s + "' (length: " + s.length() + ")");
                        Log.d(SMART_REPLY_TAG, "Conversation history size: " + conversationHistory.size());

                        if (s.length() > 0 && conversationHistory.size() > 0) {
                            Log.d(SMART_REPLY_TAG, "Conditions met for generating suggestions - scheduling...");
                            suggestionHandler.removeCallbacksAndMessages(null);
                            suggestionHandler.postDelayed(() -> {
                                Log.d(SMART_REPLY_TAG, "Executing delayed suggestion generation");
                                generateSmartReplies();
                            }, 800);
                        } else {
                            Log.d(SMART_REPLY_TAG, "Conditions not met - hiding suggestions");
                            hideSuggestions();
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

                Log.d(SMART_REPLY_TAG, "TextWatcher added to EditText successfully");
                isSmartReplyInitialized = true;
                Log.d(SMART_REPLY_TAG, "Smart Reply initialization completed successfully!");

                Toast.makeText(this, "AI Smart Reply initialized successfully!", Toast.LENGTH_SHORT).show();

            } else {
                Log.e(SMART_REPLY_TAG, "ERROR: typeMsg EditText is null!");
                Toast.makeText(this, "Input field not found - AI won't work", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            Log.e(SMART_REPLY_TAG, "CRITICAL ERROR in Smart Reply initialization: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "AI initialization failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            isSmartReplyInitialized = false;
        }
    }

    // Generate AI smart replies - CORRECTED VERSION
    // CORRECTED: Generate AI smart replies
    private void generateSmartReplies() {
        Log.d(SMART_REPLY_TAG, "Generating smart replies...");
        Log.d(SMART_REPLY_TAG, "Conversation history size: " + conversationHistory.size());

        if (!isSmartReplyInitialized || conversationHistory.isEmpty()) {
            Log.d(SMART_REPLY_TAG, "Cannot generate suggestions - insufficient data");
            hideSuggestions();
            return;
        }

        try {
            smartReply.suggestReplies(conversationHistory)
                    .addOnSuccessListener(result -> {
                        Log.d(SMART_REPLY_TAG, "ML Kit API Success - Status: " + result.getStatus());

                        if (result.getStatus() == SmartReplySuggestionResult.STATUS_SUCCESS) {
                            Log.d(SMART_REPLY_TAG, "Suggestions generated: " + result.getSuggestions().size());
                            displaySuggestions(result.getSuggestions());
                        } else if (result.getStatus() == SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE) {
                            Log.w(SMART_REPLY_TAG, "Language not supported - use English only");
                            Toast.makeText(this, "AI only works with English conversations", Toast.LENGTH_LONG).show();
                            hideSuggestions();
                        } else {
                            Log.d(SMART_REPLY_TAG, "No suggestions available");
                            hideSuggestions();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(SMART_REPLY_TAG, "ML Kit API Failed: " + e.getMessage());
                        e.printStackTrace();
                        hideSuggestions();
                    });

        } catch (Exception e) {
            Log.e(SMART_REPLY_TAG, "Exception in generateSmartReplies: " + e.getMessage());
            e.printStackTrace();
            hideSuggestions();
        }
    }


    // Display AI suggestions - TEXTVIEW VERSION (Guaranteed Fix)
    private void displaySuggestions(List<SmartReplySuggestion> suggestions) {
        Log.d(SMART_REPLY_TAG, "=== DISPLAYING SUGGESTIONS ===");
        Log.d(SMART_REPLY_TAG, "Number of suggestions to display: " + suggestions.size());

        if (suggestionsContainer == null) {
            Log.e(SMART_REPLY_TAG, "ERROR: suggestionsContainer is null!");
            return;
        }

        suggestionsContainer.removeAllViews();
        Log.d(SMART_REPLY_TAG, "Cleared existing suggestion views");

        if (suggestions.isEmpty()) {
            Log.d(SMART_REPLY_TAG, "No suggestions to display - hiding container");
            hideSuggestions();
            return;
        }

        try {
            int maxSuggestions = Math.min(suggestions.size(), 10);
            for (int i = 0; i < maxSuggestions; i++) {
                SmartReplySuggestion suggestion = suggestions.get(i);
                Log.d(SMART_REPLY_TAG, "Creating TextView for suggestion " + i + ": '" + suggestion.getText() + "'");

                // USE TEXTVIEW INSTEAD OF BUTTON - Guaranteed wrap content
                TextView suggestionButton = new TextView(this);
                suggestionButton.setText(suggestion.getText());

                // Make it clickable and focusable like a button
                suggestionButton.setClickable(true);
                suggestionButton.setFocusable(true);
                // FORCE SPECIFIC WIDTH - Add this line:
                suggestionButton.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.6)); // 60% of screen width


                // APPLY BORDERED BACKGROUND
                try {
                    suggestionButton.setBackgroundResource(R.drawable.suggestion_button_background);
                    Log.d(SMART_REPLY_TAG, "Applied background drawable successfully");
                } catch (Exception e) {
                    Log.w(SMART_REPLY_TAG, "Background drawable not found, using default: " + e.getMessage());
                    suggestionButton.setBackgroundColor(Color.LTGRAY);
                }

                try {
                    suggestionButton.setTextColor(getResources().getColor(R.color.purple_500));
                } catch (Exception e) {
                    Log.w(SMART_REPLY_TAG, "Purple color not found, using default: " + e.getMessage());
                    suggestionButton.setTextColor(Color.BLUE);
                }

                suggestionButton.setTextSize(18f);
                suggestionButton.setPadding(32, 16, 32, 16 );
                suggestionButton.setGravity(Gravity.CENTER);  // Center the text like a button

                // GUARANTEED: Use WRAP_CONTENT for both width and height
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,  // Width wraps to content
                        LinearLayout.LayoutParams.WRAP_CONTENT   // Height wraps to content
                );
                params.setMargins(6, 0, 6, 0);  // Smaller margins
                suggestionButton.setLayoutParams(params);

                suggestionButton.setOnClickListener(v -> {
                    Log.d(SMART_REPLY_TAG, "Suggestion clicked: '" + suggestion.getText() + "'");
                    typeMsg.setText(suggestion.getText());
                    typeMsg.setSelection(typeMsg.getText().length());
                    hideSuggestions();
                    Toast.makeText(this, "Suggestion applied!", Toast.LENGTH_SHORT).show();
                });

                suggestionsContainer.addView(suggestionButton);
                Log.d(SMART_REPLY_TAG, "Added suggestion TextView " + i + " to container");
            }

            if (suggestionsScrollView != null) {
                suggestionsScrollView.setVisibility(View.VISIBLE);
                Log.d(SMART_REPLY_TAG, "Made suggestions container visible");
            } else {
                Log.e(SMART_REPLY_TAG, "ERROR: suggestionsScrollView is null!");
            }

        } catch (Exception e) {
            Log.e(SMART_REPLY_TAG, "ERROR creating suggestion TextViews: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error displaying suggestions: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }





    // Hide AI suggestions
    private void hideSuggestions() {
        Log.d(SMART_REPLY_TAG, "Hiding suggestions");

        if (suggestionsScrollView != null) {
            suggestionsScrollView.setVisibility(View.GONE);
            Log.d(SMART_REPLY_TAG, "Hidden suggestions scroll view");
        }

        if (suggestionsContainer != null) {
            suggestionsContainer.removeAllViews();
            Log.d(SMART_REPLY_TAG, "Removed all suggestion views");
        }
    }

    // CORRECTED: Add messages to conversation history for AI
// CORRECTED: Add messages to conversation history for AI
    private void addToConversationHistory(String message, boolean isLocalUser) {
        Log.d(SMART_REPLY_TAG, "Adding message to conversation history: " + message);

        if (!isSmartReplyInitialized) {
            Log.w(SMART_REPLY_TAG, "Smart Reply not initialized - skipping");
            return;
        }

        try {
            FirebaseTextMessage textMessage;
            if (isLocalUser) {
                textMessage = FirebaseTextMessage.createForLocalUser(message, System.currentTimeMillis());
            } else {
                textMessage = FirebaseTextMessage.createForRemoteUser(message, System.currentTimeMillis(), "remote_user");
            }

            conversationHistory.add(textMessage);
            Log.d(SMART_REPLY_TAG, "Message added successfully. History size: " + conversationHistory.size());

            // Keep only last 10 messages
            if (conversationHistory.size() > 10) {
                conversationHistory.remove(0);
                Log.d(SMART_REPLY_TAG, "Removed oldest message. New size: " + conversationHistory.size());
            }

        } catch (Exception e) {
            Log.e(SMART_REPLY_TAG, "Error adding to conversation history: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Your existing methods continue here...
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                sendFile(fileUri);
            }
        }
    }

    private void sendFile(Uri fileUri) {
        new Thread(() -> {
            try {
                ContentResolver contentResolver = getContentResolver();
                String fileName = getFileName(fileUri);

                ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(fileUri, "r");
                long fileSize = pfd.getStatSize();
                pfd.close();

                if (isHost) {
                    serverClass.write(new byte[]{1}, 0, 1);
                } else {
                    clientClass.write(new byte[]{1}, 0, 1);
                }

                @SuppressLint({"NewApi", "LocalSuppress"}) byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
                ByteBuffer fileNameLength = ByteBuffer.allocate(4);
                fileNameLength.putInt(fileNameBytes.length);

                if (isHost) {
                    serverClass.write(fileNameLength.array(), 0, 4);
                    serverClass.write(fileNameBytes, 0, fileNameBytes.length);
                } else {
                    clientClass.write(fileNameLength.array(), 0, 4);
                    clientClass.write(fileNameBytes, 0, fileNameBytes.length);
                }

                ByteBuffer sizeBuffer = ByteBuffer.allocate(8);
                sizeBuffer.putLong(fileSize);

                if (isHost) {
                    serverClass.write(sizeBuffer.array(), 0, 8);
                } else {
                    clientClass.write(sizeBuffer.array(), 0, 8);
                }

                try (InputStream inputStream = contentResolver.openInputStream(fileUri)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalSent = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (isHost) {
                            serverClass.write(buffer, 0, bytesRead);
                        } else {
                            clientClass.write(buffer, 0, bytesRead);
                        }
                        totalSent += bytesRead;

                        final long progress = totalSent;
                        runOnUiThread(() ->
                                connectionStatus.setText("Sending: " + progress + "/" + fileSize));
                    }
                }

                File localCopy = new File(getExternalFilesDir(null), fileName);
                try (InputStream in = contentResolver.openInputStream(fileUri);
                     OutputStream out = new FileOutputStream(localCopy)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }

                runOnUiThread(() -> {
                    appendFileMessage("Sent: " + fileName, true);
                    Toast.makeText(MainActivity.this, "File sent successfully", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error sending file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void exqListener() {
        // REMOVED: Old button listeners - they don't exist in WhatsApp layout
        // The WiFi toggle is now handled by wifiToggleIcon in initializeWhatsAppUI()
        // The discover functionality is now handled by overflow menu
        // The send functionality is now handled by voiceSendButton

        // Update listView click listener for device connection
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = deviceArray[i];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;

                // Hide device list immediately
                deviceListCard.setVisibility(View.GONE);

                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Connected to " + device.deviceName);
                        Toast.makeText(MainActivity.this, "Connection successful!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reason) {
                        connectionStatus.setText("Connection failed");
                        Toast.makeText(MainActivity.this, "Connection failed!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }


    private void initialwork() {
        connectionStatus = findViewById(R.id.connection_status);
        messageContainer = findViewById(R.id.messageContainer);
       // aSwitch = findViewById(R.id.swithch1);
        //discoverButton = findViewById(R.id.buttonDiscover);
        listView = findViewById(R.id.listView);
        typeMsg = findViewById(R.id.editTextTypeMsg);
      //  sendButton = findViewById(R.id.sendButton);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WifiDirectBroadcastReceiver(this, manager, channel);
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
            if (!wifiP2pDeviceList.equals(peers)) {
                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());

                deviceNameArray = new String[wifiP2pDeviceList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[wifiP2pDeviceList.getDeviceList().size()];

                int index = 0;
                for (WifiP2pDevice device : wifiP2pDeviceList.getDeviceList()) {
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                listView.setAdapter(adapter);

                if (peers.size() == 0) {
                    connectionStatus.setText("No Device Found");
                }
            }
        }
    };

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            final InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionStatus.setText("Host");
                isHost = true;
                serverClass = new ServerClass();
                serverClass.start();

                // ADD HERE - After becoming group owner
                optimizeWifiDirectForVoice();

            } else if (wifiP2pInfo.groupFormed) {
                connectionStatus.setText("Client");
                isHost = false;
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();

                // ADD HERE - After connecting as client
                optimizeWifiDirectForVoice();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    // Your existing ServerClass and ClientClass remain the same...
    public class ServerClass extends Thread {
        ServerSocket serverSocket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public void write(byte[] bytes, int offset, int length) {
            try {
                if (outputStream != null) {
                    outputStream.write(bytes, offset, length);
                    outputStream.flush();
                }
            } catch (IOException e) {
                Log.e("ServerClass", "Write error: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Connection error", Toast.LENGTH_SHORT).show());
            }
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8888);
                Log.d("ServerClass", "Server started");

                socket = serverSocket.accept();
                Log.d("ServerClass", "Client connected");

                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                runOnUiThread(() -> optimizeWifiDirectForVoice());

                DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream));

                while (!isInterrupted() && socket != null && !socket.isClosed()) {
                    try {
                        byte messageType = dis.readByte();

                        if (messageType == 0) {
                            // Text message handling
                            int messageLength = dis.readInt();
                            byte[] messageBytes = new byte[messageLength];
                            dis.readFully(messageBytes);
                            @SuppressLint({"NewApi", "LocalSuppress"}) String message = new String(messageBytes, StandardCharsets.UTF_8);

                            Log.d(TAG, "Received message from remote: '" + message + "'");

                            // VOICE CALLING MESSAGE HANDLING - ADDED
                            if (message.equals("VOICE_CALL_REQUEST")) {
                                runOnUiThread(() -> showIncomingCallDialog());
                            } else if (message.equals("VOICE_CALL_END")) {
                                runOnUiThread(() -> endVoiceCall());
                            } else if (message.equals("VOICE_CALL_DECLINED")) {
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "Call declined", Toast.LENGTH_SHORT).show();
                                    endVoiceCall();
                                });
                            } else {
                                // Regular text message
                                runOnUiThread(() -> {
                                    appendMessageWithMeta(message, false);
                                    if (mediaPlayerReceive != null) {
                                        mediaPlayerReceive.start();
                                    }
                                });
                            }

                        } else if (messageType == 1) {
                            // File handling code remains the same
                            int fileNameLength = dis.readInt();
                            byte[] fileNameBytes = new byte[fileNameLength];
                            dis.readFully(fileNameBytes);
                            @SuppressLint({"NewApi", "LocalSuppress"}) String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);

                            long fileSize = dis.readLong();
                            File outputFile = new File(getExternalFilesDir(null), fileName);

                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                byte[] buffer = new byte[8192];
                                long remaining = fileSize;

                                while (remaining > 0) {
                                    int bytesToRead = (int) Math.min(buffer.length, remaining);
                                    int bytesRead = dis.read(buffer, 0, bytesToRead);
                                    if (bytesRead == -1) break;

                                    fos.write(buffer, 0, bytesRead);
                                    remaining -= bytesRead;

                                    final long progress = fileSize - remaining;
                                    runOnUiThread(() ->
                                            connectionStatus.setText("Receiving: " + progress + "/" + fileSize));
                                }
                            }

                            if (outputFile.length() == fileSize) {
                                final String finalFileName = fileName;
                                runOnUiThread(() -> {
                                    appendFileMessage("Received: " + finalFileName, false);
                                    if (mediaPlayerReceive != null) {
                                        mediaPlayerReceive.start();
                                    }
                                    Toast.makeText(MainActivity.this, "File received successfully", Toast.LENGTH_SHORT).show();
                                });
                            } else {
                                outputFile.delete();
                                runOnUiThread(() ->
                                        Toast.makeText(MainActivity.this, "File transfer incomplete", Toast.LENGTH_LONG).show());
                            }

                        } // In both ServerClass and ClientClass, update the messageType == 2 case:
                        else if (messageType == 2) {
                            // Voice message handling
                            long fileSize = dis.readLong();
                            String voiceFileName = "received_voice_" + System.currentTimeMillis() + ".3gp";
                            File voiceFile = new File(getExternalCacheDir(), voiceFileName);

                            try (FileOutputStream fos = new FileOutputStream(voiceFile)) {
                                byte[] buffer = new byte[1024];
                                long remaining = fileSize;
                                int bytesRead;

                                while (remaining > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                                    fos.write(buffer, 0, bytesRead);
                                    remaining -= bytesRead;
                                }

                                if (remaining == 0) {
                                    runOnUiThread(() -> {
                                        createReceivedVoiceMessage(voiceFile.getAbsolutePath());
                                        if (mediaPlayerReceive != null) {
                                            mediaPlayerReceive.start();
                                        }
                                    });
                                } else {
                                    voiceFile.delete();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error receiving voice message", e);
                                voiceFile.delete();
                            }
                        }else if (messageType == 3) {
                            // VOICE CALL AUDIO DATA HANDLING - ADDED
                            int audioDataLength = dis.readInt();
                            byte[] audioData = new byte[audioDataLength];
                            dis.readFully(audioData);

                            runOnUiThread(() -> {
                                if (voiceCallManager != null && isInVoiceCall) {
                                    voiceCallManager.playReceivedAudio(audioData);
                                }
                            });
                        }

                    } catch (EOFException e) {
                        Log.d("ServerClass", "Connection closed by client");
                        break;
                    } catch (IOException e) {
                        Log.e("ServerClass", "Read error: " + e.getMessage());
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e("ServerClass", "Server error: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void closeConnection() {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
                if (socket != null) socket.close();
                if (serverSocket != null) serverSocket.close();
            } catch (IOException e) {
                Log.e("ServerClass", "Error closing resources: " + e.getMessage());
            }

            runOnUiThread(() -> connectionStatus.setText("Disconnected"));
        }
    }


    private void createReceivedVoiceMessage(String audioFilePath) {
        runOnUiThread(() -> {
            String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

            FrameLayout wrapper = new FrameLayout(this);
            LinearLayout bubbleLayout = new LinearLayout(this);
            bubbleLayout.setOrientation(LinearLayout.HORIZONTAL);
            bubbleLayout.setBackgroundResource(R.drawable.bubble_receiver);
            bubbleLayout.setPadding(20, 14, 20, 14);
            bubbleLayout.setGravity(Gravity.CENTER_VERTICAL); // CENTER VERTICAL ALIGNMENT

            // Play button - with proper alignment
            ImageView playButton = new ImageView(this);
            playButton.setImageResource(android.R.drawable.ic_media_play);
            LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(96, 96);
            playParams.gravity = Gravity.CENTER_VERTICAL;
            playButton.setLayoutParams(playParams);

            // Voice message text - with proper alignment
            TextView voiceText = new TextView(this);
            voiceText.setText("Voice message");
            voiceText.setTextColor(Color.BLACK);
            voiceText.setTextSize(16f);
            voiceText.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, // Width 0 to use weight
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            textParams.weight = 1.0f; // Take remaining space
            textParams.setMargins(16, 0, 16, 0);
            textParams.gravity = Gravity.CENTER_VERTICAL;
            voiceText.setLayoutParams(textParams);

            // Timestamp - aligned to end
            TextView timestampText = new TextView(this);
            timestampText.setText(timestamp);
            timestampText.setTextColor(Color.GRAY);
            timestampText.setTextSize(12f);
            timestampText.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams timestampParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            timestampParams.gravity = Gravity.CENTER_VERTICAL;
            timestampText.setLayoutParams(timestampParams);

            // Add click listener with icon change functionality
            playButton.setOnClickListener(new View.OnClickListener() {
                MediaPlayer localPlayer = null;
                boolean isPlaying = false;

                @Override
                public void onClick(View v) {
                    if (!isPlaying) {
                        try {
                            if (localPlayer != null) {
                                localPlayer.release();
                            }
                            localPlayer = new MediaPlayer();
                            localPlayer.setDataSource(audioFilePath);
                            localPlayer.prepare();
                            localPlayer.start();
                            playButton.setImageResource(android.R.drawable.ic_media_pause);
                            isPlaying = true;

                            localPlayer.setOnCompletionListener(mp -> {
                                playButton.setImageResource(android.R.drawable.ic_media_play);
                                isPlaying = false;
                                localPlayer.release();
                                localPlayer = null;
                            });
                        } catch (Exception e) {
                            playButton.setImageResource(android.R.drawable.ic_media_play);
                            isPlaying = false;
                            Toast.makeText(MainActivity.this, "Error playing voice message", Toast.LENGTH_SHORT).show();
                            if (localPlayer != null) {
                                localPlayer.release();
                                localPlayer = null;
                            }
                        }
                    } else {
                        if (localPlayer != null) {
                            localPlayer.stop();
                            localPlayer.release();
                            localPlayer = null;
                        }
                        playButton.setImageResource(android.R.drawable.ic_media_play);
                        isPlaying = false;
                    }
                }
            });

            // Add views in order: Play button, Voice text, Timestamp
            bubbleLayout.addView(playButton);
            bubbleLayout.addView(voiceText);
            bubbleLayout.addView(timestampText);
            wrapper.addView(bubbleLayout);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(10, 10, 10, 10);
            params.gravity = Gravity.START;
            wrapper.setLayoutParams(params);

            messageContainer.addView(wrapper);
            scrollToBottom();
        });
    }



    private void playReceivedVoiceMessage(String audioFilePath) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
            });

        } catch (Exception e) {
            Log.e(TAG, "Error playing voice message", e);
            Toast.makeText(this, "Error playing voice message", Toast.LENGTH_SHORT).show();
        }
    }

    private void scrollToBottom() {
        ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void optimizeWifiDirectForVoice() {
        // This should be called after WiFi Direct connection is established
        try {
            WifiP2pManager.GroupInfoListener groupInfoListener = new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null) {
                        Log.d(TAG, "=== WiFi Direct Group Info ===");
                        Log.d(TAG, "Group Name: " + group.getNetworkName());
                        Log.d(TAG, "Group Password: " + group.getPassphrase());
                        Log.d(TAG, "Group Owner: " + group.getOwner().deviceName);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Log.d(TAG, "Group Frequency: " + group.getFrequency() + " MHz");
                        }
                        Log.d(TAG, "Client Count: " + group.getClientList().size());

                        // Voice call optimization based on frequency
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (group.getFrequency() > 5000) {
                                Log.d(TAG, "Using 5GHz band - Optimal for voice calls");
                                // Could adjust audio quality settings here
                            } else {
                                Log.d(TAG, "Using 2.4GHz band - Standard voice call settings");
                            }
                        }

                        // Update UI with connection info
                        runOnUiThread(() -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                connectionStatus.setText("Connected (" + group.getFrequency() + "MHz)");
                            }
                        });
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                manager.requestGroupInfo(channel, groupInfoListener);
            } else {
                Log.w(TAG, "Group info not available on this Android version");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error optimizing WiFi Direct", e);
        }
    }

    private void resetRecordingState() {
        isRecording = false;
        runOnUiThread(() -> {
            voiceSendButton.setImageResource(R.drawable.ic_mic);
            voiceSendButton.setBackgroundResource(R.drawable.circle_button_background);
        });

        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing media recorder", e);
            }
            mediaRecorder = null;
        }
    }


    public class ClientClass extends Thread {
        String hostAdd;
        private InputStream inputStream;
        private OutputStream outputStream;

        public ClientClass(InetAddress hostAddress) {
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        public void write(byte[] bytes, int offset, int length) {
            try {
                if (outputStream != null) {
                    outputStream.write(bytes, offset, length);
                    outputStream.flush();
                }
            } catch (IOException e) {
                Log.e("ClientClass", "Write error: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Connection error", Toast.LENGTH_SHORT).show());
            }
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, 8888), 5000);
                Log.d("ClientClass", "Connected to server");

                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                runOnUiThread(() -> optimizeWifiDirectForVoice());

                DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream));

                while (!isInterrupted() && socket != null && !socket.isClosed()) {
                    try {
                        byte messageType = dis.readByte();

                        if (messageType == 0) {
                            // Text message handling
                            int messageLength = dis.readInt();
                            byte[] messageBytes = new byte[messageLength];
                            dis.readFully(messageBytes);
                            @SuppressLint({"NewApi", "LocalSuppress"}) String message = new String(messageBytes, StandardCharsets.UTF_8);

                            Log.d(TAG, "Received message from remote: '" + message + "'");

                            // VOICE CALLING MESSAGE HANDLING - ADDED (same as ServerClass)
                            if (message.equals("VOICE_CALL_REQUEST")) {
                                runOnUiThread(() -> showIncomingCallDialog());
                            } else if (message.equals("VOICE_CALL_END")) {
                                runOnUiThread(() -> endVoiceCall());
                            } else if (message.equals("VOICE_CALL_DECLINED")) {
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "Call declined", Toast.LENGTH_SHORT).show();
                                    endVoiceCall();
                                });
                            } else {
                                // Regular text message
                                runOnUiThread(() -> {
                                    appendMessageWithMeta(message, false);
                                    if (mediaPlayerReceive != null) {
                                        mediaPlayerReceive.start();
                                    }
                                });
                            }

                        } else if (messageType == 1) {

                            // File handling code - FIXED
                            int fileNameLength = dis.readInt();
                            byte[] fileNameBytes = new byte[fileNameLength];
                            dis.readFully(fileNameBytes);
                            String fileName = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
                            }

                            long fileSize = dis.readLong();
                            File outputFile = new File(getExternalFilesDir(null), fileName);

                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                byte[] buffer = new byte[8192];
                                long remaining = fileSize;

                                while (remaining > 0) {
                                    int bytesToRead = (int) Math.min(buffer.length, remaining);
                                    int bytesRead = dis.read(buffer, 0, bytesToRead);
                                    if (bytesRead == -1) break;

                                    fos.write(buffer, 0, bytesRead);
                                    remaining -= bytesRead;

                                    final long progress = fileSize - remaining;
                                    runOnUiThread(() ->
                                            connectionStatus.setText("Receiving: " + progress + "/" + fileSize));
                                }
                            }

                            if (outputFile.length() == fileSize) {
                                final String finalFileName = fileName;
                                runOnUiThread(() -> {
                                    appendFileMessage("Received: " + finalFileName, false);
                                    if (mediaPlayerReceive != null) {
                                        mediaPlayerReceive.start();
                                    }
                                    Toast.makeText(MainActivity.this, "File received successfully", Toast.LENGTH_SHORT).show();
                                });
                            } else {
                                outputFile.delete();
                                runOnUiThread(() ->
                                        Toast.makeText(MainActivity.this, "File transfer incomplete", Toast.LENGTH_LONG).show());
                            }




                        } // In both ServerClass and ClientClass, update the messageType == 2 case:
                        else if (messageType == 2) {
                            // Voice message handling
                            long fileSize = dis.readLong();
                            String voiceFileName = "received_voice_" + System.currentTimeMillis() + ".3gp";
                            File voiceFile = new File(getExternalCacheDir(), voiceFileName);

                            try (FileOutputStream fos = new FileOutputStream(voiceFile)) {
                                byte[] buffer = new byte[1024];
                                long remaining = fileSize;
                                int bytesRead;

                                while (remaining > 0 && (bytesRead = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                                    fos.write(buffer, 0, bytesRead);
                                    remaining -= bytesRead;
                                }

                                if (remaining == 0) {
                                    runOnUiThread(() -> {
                                        createReceivedVoiceMessage(voiceFile.getAbsolutePath());
                                        if (mediaPlayerReceive != null) {
                                            mediaPlayerReceive.start();
                                        }
                                    });
                                } else {
                                    voiceFile.delete();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error receiving voice message", e);
                                voiceFile.delete();
                            }
                        } else if (messageType == 3) {
                            // VOICE CALL AUDIO DATA HANDLING - ADDED (same as ServerClass)
                            int audioDataLength = dis.readInt();
                            byte[] audioData = new byte[audioDataLength];
                            dis.readFully(audioData);

                            runOnUiThread(() -> {
                                if (voiceCallManager != null && isInVoiceCall) {
                                    voiceCallManager.playReceivedAudio(audioData);
                                }
                            });
                        }

                    } catch (EOFException e) {
                        Log.d("ClientClass", "Connection closed by server");
                        break;
                    } catch (IOException e) {
                        Log.e("ClientClass", "Read error: " + e.getMessage());
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e("ClientClass", "Connection error: " + e.getMessage());
            } finally {
                closeConnection();
            }
        }

        private void closeConnection() {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                Log.e("ClientClass", "Error closing resources: " + e.getMessage());
            }

            runOnUiThread(() -> connectionStatus.setText("Disconnected"));
        }
    }


    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                } else {
                    result = path;
                }
            }
        }

        return result;
    }

    private void appendFileMessage(String message, boolean isSender) {
        String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String fileName = message.replace("Sent: ", "").replace("Received: ", "").trim();
        File file = new File(getExternalFilesDir(null), fileName);

        FrameLayout wrapper = new FrameLayout(this);
        LinearLayout bubbleLayout = new LinearLayout(this);
        bubbleLayout.setOrientation(LinearLayout.VERTICAL);

        try {
            bubbleLayout.setBackgroundResource(isSender ? R.drawable.bubble_sender : R.drawable.bubble_receiver);
        } catch (Exception e) {
            bubbleLayout.setBackgroundColor(isSender ? Color.LTGRAY : Color.WHITE);
        }

        bubbleLayout.setPadding(20, 14, 20, 14);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleLayout.setLayoutParams(bubbleParams);

        if (file.exists()) {
            if (fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".jpeg")) {
                try {
                    ImageView imageView = new ImageView(this);
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

                    int maxWidth = (int)(getResources().getDisplayMetrics().widthPixels * 0.6);
                    int maxHeight = (int)(getResources().getDisplayMetrics().heightPixels * 0.4);

                    if (bitmap.getWidth() > maxWidth || bitmap.getHeight() > maxHeight) {
                        float aspectRatio = (float)bitmap.getWidth() / (float)bitmap.getHeight();
                        if (aspectRatio > 1) {
                            bitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, (int)(maxWidth / aspectRatio), true);
                        } else {
                            bitmap = Bitmap.createScaledBitmap(bitmap, (int)(maxHeight * aspectRatio), maxHeight, true);
                        }
                    }

                    imageView.setImageBitmap(bitmap);
                    imageView.setAdjustViewBounds(true);
                    imageView.setPadding(0, 0, 0, 10);

                    imageView.setOnClickListener(v -> openFile(file, "image/*"));
                    bubbleLayout.addView(imageView);

                    TextView timestampText = new TextView(this);
                    timestampText.setText(timestamp);
                    timestampText.setTextColor(Color.GRAY);
                    timestampText.setTextSize(12f);
                    timestampText.setGravity(Gravity.END);
                    bubbleLayout.addView(timestampText);

                } catch (Exception e) {
                    TextView errorText = new TextView(this);
                    errorText.setText("Couldn't load image");
                    bubbleLayout.addView(errorText);
                }
            } else {
                TextView fileText = new TextView(this);
                fileText.setText("📄 " + fileName + "  " + timestamp);
                fileText.setTextColor(Color.BLUE);
                fileText.setTextSize(15f);
                fileText.setPadding(10, 10, 10, 10);
                fileText.setBackgroundColor(Color.parseColor("#f0f0f0"));

                fileText.setOnClickListener(v -> openFile(file, null));
                bubbleLayout.addView(fileText);
            }
        }

        wrapper.addView(bubbleLayout);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(10, 10, 10, 10);
        wrapperParams.gravity = isSender ? Gravity.END : Gravity.START;
        wrapper.setLayoutParams(wrapperParams);

        wrapper.setOnLongClickListener(v -> {
            showDeleteDialog(wrapper, file);
            return true;
        });

        messageContainer.addView(wrapper);

        if (!isSender && mediaPlayerReceive != null) {
            mediaPlayerReceive.start();
        } else if (isSender && mediaPlayerSend != null) {
            mediaPlayerSend.start();
        }

        ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void openFile(File file, String forcedMimeType) {
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    MainActivity.this,
                    getPackageName() + ".fileprovider",
                    file
            );

            String mimeType = forcedMimeType;
            if (mimeType == null) {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".pdf")) {
                    mimeType = "application/pdf";
                } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                    mimeType = "application/msword";
                } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                    mimeType = "application/vnd.ms-excel";
                } else if (fileName.endsWith(".ppt") || fileName.endsWith(".pptx")) {
                    mimeType = "application/vnd.ms-powerpoint";
                } else if (fileName.endsWith(".txt")) {
                    mimeType = "text/plain";
                } else {
                    mimeType = getContentResolver().getType(contentUri);
                    if (mimeType == null) {
                        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.getName());
                        mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                        if (mimeType == null) mimeType = "*/*";
                    }
                }
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(intent);
            } catch (Exception e) {
                intent.setDataAndType(contentUri, "*/*");
                try {
                    startActivity(intent);
                } catch (Exception e2) {
                    Toast.makeText(MainActivity.this,
                            "No app available to open this file type",
                            Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(MainActivity.this,
                    "Couldn't open file: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void appendMessageWithMeta(String message, boolean isSender) {
        Log.d(TAG, "Appending message: '" + message + "', isSender: " + isSender);

        // Add to AI conversation history
        addToConversationHistory(message, isSender);

        String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        String quotedText = null;
        String actualMessage = message;

        if (message.startsWith("Replying to: ")) {
            int index = message.indexOf("\n");
            if (index != -1) {
                quotedText = message.substring(0, index);
                actualMessage = message.substring(index + 1);
            }
        }

        FrameLayout wrapper = new FrameLayout(this);

        LinearLayout bubbleLayout = new LinearLayout(this);
        bubbleLayout.setOrientation(LinearLayout.VERTICAL);

        try {
            bubbleLayout.setBackgroundResource(isSender ? R.drawable.bubble_sender : R.drawable.bubble_receiver);
        } catch (Exception e) {
            bubbleLayout.setBackgroundColor(isSender ? Color.LTGRAY : Color.WHITE);
        }

        bubbleLayout.setPadding(20, 14, 20, 14);

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleLayout.setLayoutParams(bubbleParams);

        if (quotedText != null) {
            TextView replyQuote = new TextView(this);
            replyQuote.setText(quotedText);
            replyQuote.setTextColor(Color.parseColor("#1e88e5"));
            replyQuote.setBackgroundColor(Color.parseColor("#e3f2fd"));
            replyQuote.setPadding(12, 8, 12, 8);
            replyQuote.setTextSize(13f);
            bubbleLayout.addView(replyQuote);
        }

        // UPDATED: Create separate message TextView without timestamp
        TextView msgText = new TextView(this);
        msgText.setText(actualMessage);  // Only message text, no timestamp
        msgText.setTextColor(Color.BLACK);
        msgText.setTextSize(16f);
        msgText.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75));
        bubbleLayout.addView(msgText);

        // NEW: Create separate timestamp TextView positioned at bottom-right
        TextView timestampText = new TextView(this);
        timestampText.setText(timestamp);
        timestampText.setTextColor(Color.GRAY);
        timestampText.setTextSize(12f);
        timestampText.setGravity(Gravity.END);  // Align to right

        // Add some top margin to separate from message
        LinearLayout.LayoutParams timestampParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timestampParams.setMargins(0, 8, 0, 0);  // 8dp top margin
        timestampText.setLayoutParams(timestampParams);

        bubbleLayout.addView(timestampText);

        // Handle image attachments (existing code)
        if ((actualMessage.contains("/storage") || actualMessage.contains(getExternalFilesDir(null).getAbsolutePath()))
                && (actualMessage.endsWith(".jpg") || actualMessage.endsWith(".png") || actualMessage.endsWith(".jpeg"))) {

            File imageFile = new File(actualMessage.trim());
            if (imageFile.exists()) {
                ImageView imageView = new ImageView(this);
                imageView.setImageURI(Uri.fromFile(imageFile));
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(500, 500);
                imageParams.setMargins(0, 10, 0, 0);
                imageView.setLayoutParams(imageParams);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                bubbleLayout.addView(imageView);
            }
        }

        wrapper.addView(bubbleLayout);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(10, 10, 10, 10);
        wrapperParams.gravity = isSender ? Gravity.END : Gravity.START;
        wrapper.setLayoutParams(wrapperParams);

        wrapper.setOnLongClickListener(v -> {
            showDeleteDialog(wrapper, null);
            return true;
        });

        String finalActualMessage = actualMessage;
        wrapper.setOnTouchListener(new OnSwipeTouchListener(this) {
            public void onSwipeRight() {
                replyLayout.setVisibility(View.VISIBLE);
                replyingToMessage = finalActualMessage;
                replyText.setText(finalActualMessage);
                replyText.setTextColor(Color.parseColor("#FF5722"));
            }
        });

        messageContainer.addView(wrapper);
        if (!isSender && mediaPlayerReceive != null) {
            mediaPlayerReceive.start();
        } else if (isSender && mediaPlayerSend != null) {
            mediaPlayerSend.start();
        }

        ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }


    private void showDeleteDialog(View messageView, File fileToDelete) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Message");
        builder.setMessage("Are you sure you want to delete this message?");

        builder.setPositiveButton("Delete", (dialog, which) -> {
            messageContainer.removeView(messageView);

            if (fileToDelete != null && fileToDelete.exists()) {
                boolean deleted = fileToDelete.delete();
                if (deleted) {
                    Toast.makeText(this, "File deleted successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.show();
    }

    public class OnSwipeTouchListener implements View.OnTouchListener {
        private final GestureDetector gestureDetector;

        public OnSwipeTouchListener(Context context) {
            gestureDetector = new GestureDetector(context, new GestureListener());
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }

        private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(e2.getY() - e1.getY())) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight();
                            return true;
                        }
                    }
                }
                return false;
            }
        }

        public void onSwipeRight() {
            // Override in usage
        }
    }

    private void saveThemePreference(int theme) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(THEME_KEY, theme);
        editor.apply();
        Log.d(TAG, "Theme preference saved: " + theme);
    }

    private int getThemePreference() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(THEME_KEY, THEME_LIGHT); // Default to Light theme
    }

    private void applyTheme(int theme) {
        switch (theme) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                Log.d(TAG, "Applied light theme");
                break;
            case THEME_DARK:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                Log.d(TAG, "Applied dark theme");
                break;
        }
    }


}

package com.example.wifidirectchat;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.Image;
import android.net.InetAddresses;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

public class MainActivity extends AppCompatActivity {
    TextView connectionStatus, messageTextView;
    Button aSwitch, discoverButton;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatLayout = findViewById(R.id.chatLayout);

        Button sendFileButton = findViewById(R.id.button_send_file);
        sendFileButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
        });

        mediaPlayerSend = MediaPlayer.create(this, R.raw.send_sound);
        mediaPlayerReceive = MediaPlayer.create(this, R.raw.receive_sound);

        Button clearChatButton = findViewById(R.id.clearChatButton);
        messageContainer = findViewById(R.id.messageContainer);

        clearChatButton.setOnClickListener(v -> {
            messageContainer.removeAllViews();
        });

        initialwork();

        Button toggleButton = findViewById(R.id.toggleDeviceListButton);
        ListView deviceListView = findViewById(R.id.listView);
        toggleButton.setOnClickListener(v -> {
            if (deviceListView.getVisibility() == View.VISIBLE) {
                deviceListView.setVisibility(View.GONE);
                toggleButton.setText("Show Device List");
            } else {
                deviceListView.setVisibility(View.VISIBLE);
                toggleButton.setText("Hide Device List");
            }
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

                byte[] fileNameBytes = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
                }
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
        aSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                startActivityForResult(intent, 1);
            }
        });
        discoverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Discovery started");
                    }

                    @Override
                    public void onFailure(int reason) {
                        connectionStatus.setText("Discovery not started");
                    }
                });
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                final WifiP2pDevice device = deviceArray[i];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Connected " + device.deviceAddress);
                    }

                    @Override
                    public void onFailure(int reason) {
                        connectionStatus.setText("Not Connected");
                    }
                });
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = typeMsg.getText().toString().trim();
                if (!msg.isEmpty()) {
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
                            byte[] messageBytes = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                messageBytes = finalMsg.getBytes(StandardCharsets.UTF_8);
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

                            runOnUiThread(() -> typeMsg.setText(""));
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this, "Error sending message", Toast.LENGTH_SHORT).show());
                        }
                    });
                }
            }
        });
    }

    private void initialwork() {
        connectionStatus = findViewById(R.id.connection_status);
        messageContainer = findViewById(R.id.messageContainer);
        aSwitch = findViewById(R.id.swithch1);
        discoverButton = findViewById(R.id.buttonDiscover);
        listView = findViewById(R.id.listView);
        typeMsg = findViewById(R.id.editTextTypeMsg);
        sendButton = findViewById(R.id.sendButton);

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
                    return;
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
            } else if (wifiP2pInfo.groupFormed) {
                connectionStatus.setText("Client");
                isHost = false;
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
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

                DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream));

                while (!isInterrupted() && socket != null && !socket.isClosed()) {
                    try {
                        byte messageType = dis.readByte();

                        if (messageType == 0) {
                            int messageLength = dis.readInt();
                            byte[] messageBytes = new byte[messageLength];
                            dis.readFully(messageBytes);
                            String message;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                message = new String(messageBytes, StandardCharsets.UTF_8);
                            } else {
                                message = null;
                            }

                            runOnUiThread(() -> {
                                appendMessageWithMeta(message, false);
                                if (mediaPlayerReceive != null) {
                                    mediaPlayerReceive.start();
                                }
                            });

                        } else if (messageType == 1) {
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

                DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream));

                while (!isInterrupted() && socket != null && !socket.isClosed()) {
                    try {
                        byte messageType = dis.readByte();

                        if (messageType == 0) {
                            int messageLength = dis.readInt();
                            byte[] messageBytes = new byte[messageLength];
                            dis.readFully(messageBytes);
                            String message;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                message = new String(messageBytes, StandardCharsets.UTF_8);
                            } else {
                                message = null;
                            }

                            runOnUiThread(() -> {
                                appendMessageWithMeta(message, false);
                                if (mediaPlayerReceive != null) {
                                    mediaPlayerReceive.start();
                                }
                            });

                        } else if (messageType == 1) {
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
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Failed to connect", Toast.LENGTH_SHORT).show());
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
        bubbleLayout.setBackgroundResource(isSender ? R.drawable.bubble_sender : R.drawable.bubble_receiver);
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
        bubbleLayout.setBackgroundResource(isSender ? R.drawable.bubble_sender : R.drawable.bubble_receiver);
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

        TextView msgText = new TextView(this);
        msgText.setText(actualMessage + "  " + timestamp);
        msgText.setTextColor(Color.BLACK);
        msgText.setTextSize(16f);
        msgText.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75));
        bubbleLayout.addView(msgText);

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
            boolean result = gestureDetector.onTouchEvent(event);
            if (!result) {
                return false; // Allow other touch listeners (like long press) to work
            }
            return result;
        }

        private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return false; // FIXED: Changed from true to false to allow long press
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
}

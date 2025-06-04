package com.example.wifidirectchat;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.media.MediaPlayer;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
    LinearLayout messageContainer;  // ✅ Add this line
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
    ActivityResultLauncher<Intent> wifiSettingsLauncher; //// NEW

     LinearLayout replyLayout;
     TextView replyText;
     ImageView closeReply;
     String replyingToMessage = null;
    MediaPlayer mediaPlayerSend;
    MediaPlayer mediaPlayerReceive;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize media player with sound resource
        mediaPlayerSend = MediaPlayer.create(this, R.raw.send_sound);
        mediaPlayerReceive = MediaPlayer.create(this, R.raw.receive_sound);

        Button clearChatButton = findViewById(R.id.clearChatButton);
        messageContainer = findViewById(R.id.messageContainer);

        clearChatButton.setOnClickListener(v -> {
            messageContainer.removeAllViews(); // Clears all message bubbles
        });

        initialwork(); // important

        // 🔽 Add toggle logic here
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

        exqListener(); // keep this after toggle logic

        replyLayout = findViewById(R.id.replyLayout);
        replyText = findViewById(R.id.replyText);
        closeReply = findViewById(R.id.closeReply);

        closeReply.setOnClickListener(v -> {
            replyingToMessage = null;
            replyLayout.setVisibility(View.GONE);
        });
    }



    private void exqListener() {
        aSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                startActivityForResult(intent, 1); // UPDATED
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

                    // Play sound on send
                    if(mediaPlayerSend != null){
                        mediaPlayerSend.start();
                    }


                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    String finalMsg = msg;
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (isHost) {
                                serverClass.write(finalMsg.getBytes());
                            } else {
                                clientClass.write(finalMsg.getBytes());
                            }

                            // ✅ Clear input box
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    typeMsg.setText("");
                                }
                            });
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

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8888);
                socket = serverSocket.accept();
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;
                    while (socket != null) {
                        try {
                            bytes = inputStream.read(buffer);
                            if (bytes > 0) {
                                int finalBytes = bytes;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String tempMSG = new String(buffer, 0, finalBytes);
                                        appendMessageWithMeta( tempMSG,false);

                                    }
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
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

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd, 8888), 500);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;
                    while (socket != null) {
                        try {
                            bytes = inputStream.read(buffer);
                            if (bytes > 0) {
                                int finalBytes = bytes;
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String tempMSG = new String(buffer, 0, finalBytes);
                                        appendMessageWithMeta(tempMSG,false);

                                    }
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }
    private void appendMessageWithMeta(String message, boolean isSender) {
        String timestamp = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        // Extract reply portion if present
        String quotedText = null;
        String actualMessage = message;

        if (message.startsWith("Replying to: ")) {
            int index = message.indexOf("\n");
            if (index != -1) {
                quotedText = message.substring(0, index); // Replying to: xyz
                actualMessage = message.substring(index + 1);
            }
        }

        FrameLayout wrapper = new FrameLayout(this);

        // 🧱 Container to hold reply + actual message
        LinearLayout bubbleLayout = new LinearLayout(this);
        bubbleLayout.setOrientation(LinearLayout.VERTICAL);
        bubbleLayout.setBackgroundResource(isSender ? R.drawable.bubble_sender : R.drawable.bubble_receiver);
        bubbleLayout.setPadding(20, 14, 20, 14);
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
// ✅ Removed width override
        bubbleLayout.setLayoutParams(bubbleParams);

// 🟦 Quoted message block (if any)
        if (quotedText != null) {
            TextView replyQuote = new TextView(this);
            replyQuote.setText(quotedText);
            replyQuote.setTextColor(Color.parseColor("#1e88e5")); // blue color
            replyQuote.setBackgroundColor(Color.parseColor("#e3f2fd")); // light blue bg
            replyQuote.setPadding(12, 8, 12, 8);
            replyQuote.setTextSize(13f);

            bubbleLayout.addView(replyQuote);
        }

// ✉️ Main message
        TextView msgText = new TextView(this);
        msgText.setText(actualMessage + "  " + timestamp);
        msgText.setTextColor(Color.BLACK);
        msgText.setTextSize(16f);

// ✅ Constrain long messages to wrap
        msgText.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels * 0.75));

        bubbleLayout.addView(msgText);

// Add bubbleLayout into wrapper
        wrapper.addView(bubbleLayout);


        // Set LayoutParams for wrapper
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(10, 10, 10, 10);
        wrapperParams.gravity = isSender ? Gravity.END : Gravity.START;

        wrapper.setLayoutParams(wrapperParams);

        // 💬 Swipe reply trigger
        String finalActualMessage = actualMessage;
        wrapper.setOnTouchListener(new OnSwipeTouchListener(this) {
            public void onSwipeRight() {
                replyLayout.setVisibility(View.VISIBLE);
                replyingToMessage = finalActualMessage;
                replyText.setText(finalActualMessage);
                replyText.setTextColor(Color.parseColor("#FF5722"));
            }
        });

        // Add to chat
        messageContainer.addView(wrapper);
        if (!isSender && mediaPlayerReceive  != null) {
            mediaPlayerReceive .start();
        }else if (isSender && mediaPlayerSend != null) {
            mediaPlayerSend.start();
        }


        ScrollView scrollView = findViewById(R.id.scrollView);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
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
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(e2.getY() - e1.getY())) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) onSwipeRight();
                        return true;
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

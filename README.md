# Wi-Fi Direct Chat App

A simple Android chat app using **Wi-Fi Direct sockets** to enable peer-to-peer text messaging without an internet connection.

---

## Features

- Peer-to-peer messaging over Wi-Fi Direct.
- Separate client and server socket handling.
- Real-time chat UI with sender/receiver message bubbles.
- Supports basic message and files sending and receiving with replying to specific message and timestamp .
- Deletion of messages and files
- Designed for learning socket programming on Android.

---

## Getting Started

### Prerequisites

- Android Studio (latest recommended)
- Android device or emulator with Wi-Fi Direct support
- Minimum SDK: 21 (Android 5.0 Lollipop)

---

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/yourusername/wifidirect-chat.git
   cd wifidirect-chat
2. Open the project in Android Studio.

3. Build and run on two devices/emulators.

### Usage
1. Launch the app on two devices connected to the same Wi-Fi Direct group.

2. One device will act as host (server), the other as client.

3. Type messages in the input box and hit Send.

4. Messages will appear on both devices.
### Important Notes
- Currently, only text messages are fully supported.

- File transfer feature is not enabled due to bugs.

- Both devices must be connected over Wi-Fi Direct (P2P).

## Required Permissions

This app requires the following permissions declared in your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

## Screenshot

![App Screenshot](image1.JPG)
![App Screenshot](image2.JPG)
![App Screenshot](image3.JPG)



package com.pjinkim.arcore_data_logger;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.SystemClock;


public class MainActivity extends AppCompatActivity implements ToastInterface, ARCoreSession.BluetoothDataSender {

    private static final String TAG = "MainActivity";
    // ARCore properties
    private static final String LOG_TAG = MainActivity.class.getName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private static final int REQUEST_CODE_ANDROID = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
    };

    private ARCoreSession mARCoreSession;
    private ConnectedBluetoothThread mThreadConnectedBluetooth;
    private boolean mIsBluetoothConnected = false;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private PowerManager.WakeLock mWakeLock;

    private TextView mLabelNumberFeatures, mLabelUpdateRate;
    private TextView mLabelTrackingStatus, mLabelTrackingFailureReason;

    private Button mStartStopButton;
    private TextView mLabelInterfaceTime;
    private Timer mInterfaceTimer = new Timer();
    private int mSecondCounter = 0;

    // Bluetooth properties
    private TextView mTvBluetoothStatus;
    private TextView mTvReceiveData;
    private Button mBtnBluetoothOn;
    private Button mBtnBluetoothOff;
    private Button mBtnConnect;

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mBluetoothHandler;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothSocket mBluetoothSocket;

    private static final int BT_REQUEST_ENABLE = 1;
    private static final int BT_MESSAGE_READ = 2;
    private static final int BT_CONNECTING_STATUS = 3;
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        initializeViews();
        setupBluetoothButtons();

        mARCoreSession = new ARCoreSession(this);
        mARCoreSession.setBluetoothDataSender(this);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sensors_data_logger:wakelocktag");
        mWakeLock.acquire();

        displayARCoreInformation();
        mLabelInterfaceTime.setText(R.string.ready_title);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        requestPermissions();

        setupButtonListeners();
        setupBluetoothHandler();
    }

    private void setupBluetoothButtons() {
        mBtnBluetoothOn.setOnClickListener(v -> bluetoothOn());
        mBtnBluetoothOff.setOnClickListener(v -> bluetoothOff());
        mBtnConnect.setOnClickListener(v -> listPairedDevices());
    }

    private void initializeViews() {
        // ARCore views
        mLabelNumberFeatures = findViewById(R.id.label_number_features);
        mLabelTrackingStatus = findViewById(R.id.label_tracking_status);
        mLabelTrackingFailureReason = findViewById(R.id.label_tracking_failure_reason);
        mLabelUpdateRate = findViewById(R.id.label_update_rate);
        mStartStopButton = findViewById(R.id.button_start_stop);
        mLabelInterfaceTime = findViewById(R.id.label_interface_time);

        // Bluetooth views
        mTvReceiveData = findViewById(R.id.tvReceiveData);
        mBtnBluetoothOn = findViewById(R.id.btnBluetoothOn);
        mBtnBluetoothOff = findViewById(R.id.btnBluetoothOff);
        mBtnConnect = findViewById(R.id.btnConnect);
    }

    private void setupButtonListeners() {
        mStartStopButton.setOnClickListener(this::startStopRecording);
        mBtnBluetoothOn.setOnClickListener(v -> bluetoothOn());
        mBtnBluetoothOff.setOnClickListener(v -> bluetoothOff());
        mBtnConnect.setOnClickListener(v -> listPairedDevices());
    }

    private void setupBluetoothHandler() {
        mBluetoothHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(android.os.Message msg) {
                if (msg.what == BT_MESSAGE_READ) {
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    mTvReceiveData.setText(readMessage);
                }
            }
        };
    }

    private void requestPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_CODE_ANDROID);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_ANDROID) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "All permissions are required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void startStopRecording(View view) {
        if (!mIsRecording.get()) {
            startRecording();
            startInterfaceTimer();
            if (mIsBluetoothConnected) {
                mARCoreSession.startTransmitting();
                //howToast("데이터 전송 시작");
            } else {
                showToast("Bluetooth is not connected.");
            }
        } else {
            stopRecording();
            stopInterfaceTimer();
            mARCoreSession.stopTransmitting();
            //showToast("데이터 전송 중지");
        }
    }

    @Override
    public void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean isBluetoothConnected() {
        return mIsBluetoothConnected;
    }

    @Override
    public void sendBluetoothData(String data) {
        if (mThreadConnectedBluetooth != null) {
            mThreadConnectedBluetooth.write(data);
        }
    }

    // Bluetooth 연결 성공 시 호출
    private void onBluetoothConnected() {
        mIsBluetoothConnected = true;
        runOnUiThread(() -> {
            Toast.makeText(this, "Bluetooth is connected", Toast.LENGTH_SHORT).show();
            if (mIsRecording.get()) {
                mARCoreSession.startTransmitting();
                //showToast("데이터 전송 시작");
            }
        });
    }

    // Bluetooth 연결 해제 시 호출
    private void onBluetoothDisconnected() {
        mIsBluetoothConnected = false;
        mARCoreSession.stopTransmitting();
        runOnUiThread(() -> {
            if (mIsRecording.get()) {
                //showToast("데이터 전송 중지");
            }
        });
    }


    private void startRecording() {
        try {
            OutputDirectoryManager folder = new OutputDirectoryManager("", "R_pjinkim_ARCore");
            String outputFolder = folder.getOutputDirectory();
            mARCoreSession.startSession(outputFolder);
            mIsRecording.set(true);
            updateStartStopButtonUI(true);
            //showToast("Recording starts!");
        } catch (IOException e) {
            Log.e(LOG_TAG, "startRecording: Cannot create output folder.", e);
        }
    }

    private void stopRecording() {
        mHandler.post(() -> {
            mARCoreSession.stopSession();
            mIsRecording.set(false);
            //showToast("Recording stops!");
            updateUIAfterStop();
        });
    }

    private void startInterfaceTimer() {
        mSecondCounter = 0;
        if (mInterfaceTimer != null) {
            mInterfaceTimer.cancel();
        }
        mInterfaceTimer = new Timer();
        mInterfaceTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                mSecondCounter++;
                runOnUiThread(() -> mLabelInterfaceTime.setText(interfaceIntTime(mSecondCounter)));
            }
        }, 0, 1000);
    }

    private void stopInterfaceTimer() {
        if (mInterfaceTimer != null) {
            mInterfaceTimer.cancel();
            mInterfaceTimer = null;
        }
        runOnUiThread(() -> mLabelInterfaceTime.setText(R.string.ready_title));
    }

    // 기존의 updateStartStopButtonUI 메서드를 다음과 같이 수정
    private void updateStartStopButtonUI(boolean isRecording) {
        runOnUiThread(() -> {
            mStartStopButton.setEnabled(true);
            mStartStopButton.setText(isRecording ? R.string.stop_title : R.string.start_title);
        });
    }

    private void resetUI() {
        runOnUiThread(() -> {
            mLabelNumberFeatures.setText("N/A");
            mLabelTrackingStatus.setText("N/A");
            mLabelTrackingFailureReason.setText("N/A");
            mLabelUpdateRate.setText("N/A");
            updateStartStopButtonUI(false);
        });
    }



    private void displayARCoreInformation() {
        int numberOfFeatures = mARCoreSession.getNumberOfFeatures();
        TrackingState trackingState = mARCoreSession.getTrackingState();
        TrackingFailureReason trackingFailureReason = mARCoreSession.getTrackingFailureReason();
        double updateRate = mARCoreSession.getUpdateRate();

        runOnUiThread(() -> {
            mLabelNumberFeatures.setText(String.format(Locale.US, "%05d", numberOfFeatures));
            mLabelTrackingStatus.setText(trackingState != null ? trackingState.toString() : "N/A");
            mLabelTrackingFailureReason.setText(trackingFailureReason != null ? trackingFailureReason.toString() : "N/A");
            mLabelUpdateRate.setText(String.format(Locale.US, "%.3f Hz", updateRate));
        });

        mHandler.postDelayed(this::displayARCoreInformation, 100);
    }

    private void bluetoothOn() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "This device doesn't support Bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, BT_REQUEST_ENABLE);
            } else {
                Toast.makeText(getApplicationContext(), "Bluetooth is already on", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void bluetoothOff() {
        if (mBluetoothAdapter.isEnabled()) {
            disconnectBluetooth();
            mBluetoothAdapter.disable();
            Toast.makeText(getApplicationContext(), "Bluetooth disconnected.", Toast.LENGTH_SHORT).show();

            // 녹화 중이라면 녹화 중지
            if (mIsRecording.get()) {
                stopRecording();
                stopInterfaceTimer();
                mARCoreSession.stopTransmitting();
                showToast("Stop recording and sending");

                // UI 업데이트
                updateUIAfterStop();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth is already turned off.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUIAfterStop() {
        runOnUiThread(() -> {
            mIsRecording.set(false);
            mStartStopButton.setText(R.string.start_title);
            mLabelInterfaceTime.setText(R.string.ready_title);
            resetUI();
        });
    }


    private void disconnectBluetooth() {
        if (mThreadConnectedBluetooth != null) {
            mThreadConnectedBluetooth.cancel();
            mThreadConnectedBluetooth = null;
        }
        if (mBluetoothSocket != null) {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
            mBluetoothSocket = null;
        }
        mIsBluetoothConnected = false;
        onBluetoothDisconnected();
    }




    private void listPairedDevices() {
        if (mBluetoothAdapter.isEnabled()) {
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Select a device");

                ArrayList<String> listItems = new ArrayList<>();
                for (BluetoothDevice device : pairedDevices) {
                    listItems.add(device.getName());
                }
                CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);

                builder.setItems(items, (dialog, which) -> connectSelectedDevice(items[which].toString()));
                AlertDialog alert = builder.create();
                alert.show();
            } else {
                Toast.makeText(getApplicationContext(), "No paired devices found", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Turn on Bluetooth to connect", Toast.LENGTH_LONG).show();
        }
    }

    private void connectSelectedDevice(String deviceName) {
        for (BluetoothDevice tempDevice : mBluetoothAdapter.getBondedDevices()) {
            if (deviceName.equals(tempDevice.getName())) {
                mBluetoothDevice = tempDevice;
                break;
            }
        }

        try {
            mBluetoothSocket = mBluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);
            mBluetoothSocket.connect();
            mThreadConnectedBluetooth = new ConnectedBluetoothThread(mBluetoothSocket);
            mThreadConnectedBluetooth.start();
            onBluetoothConnected();  // 연결 상태 업데이트
            mBluetoothHandler.obtainMessage(BT_CONNECTING_STATUS, 1, -1).sendToTarget();
        } catch (IOException e) {
            onBluetoothDisconnected();  // 연결 실패 시 상태 업데이트
            Toast.makeText(getApplicationContext(), "Error occurred while connecting to Bluetooth device", Toast.LENGTH_LONG).show();
        }

    }

    private String interfaceIntTime(int second) {
        if (second < 0) {
            Log.e(LOG_TAG, "interfaceIntTime: Second cannot be negative.");
            return "00:00:00";
        }
        int hours = second / 3600;
        int minutes = (second % 3600) / 60;
        int seconds = second % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        if (mIsRecording.get()) {
            stopRecording();
        }
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (mThreadConnectedBluetooth != null) {
            mThreadConnectedBluetooth.cancel();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case BT_REQUEST_ENABLE:
                if (resultCode == RESULT_OK) {
                    Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_LONG).show();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(getApplicationContext(), "Bluetooth turn on canceled", Toast.LENGTH_LONG).show();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }



    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(LOG_TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(LOG_TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    private class ConnectedBluetoothThread extends Thread {
        private static final String THREAD_TAG = "ConnectedBluetoothThread";
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedBluetoothThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(THREAD_TAG, "Socket creation failed", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void write(String str) {
            byte[] bytes = (str + "\n").getBytes();  // 줄바꿈 문자 추가
            try {
                mmOutStream.write(bytes);
                mmOutStream.flush();
            } catch (IOException e) {
                Log.e(THREAD_TAG, "Error occurred when sending data", e);
            }
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        SystemClock.sleep(100);
                        bytes = mmInStream.available();
                        bytes = mmInStream.read(buffer, 0, bytes);
                        mBluetoothHandler.obtainMessage(BT_MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }



        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(THREAD_TAG, "Could not close the connect socket", e);
            }
        }
    }
}
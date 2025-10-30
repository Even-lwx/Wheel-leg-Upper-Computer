package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final IBinder binder = new LocalBinder();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket sppSocket;
    private BluetoothDevice connectedDevice;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean isReading = false;
    private boolean isConnected = false;

    // 蓝牙连接状态监听器
    public interface BluetoothConnectionListener {
        void onBluetoothConnected(BluetoothDevice device);
        void onBluetoothDisconnected();
        void onBluetoothConnectionFailed(String error);
        void onDataReceived(String data);
    }

    private BluetoothConnectionListener connectionListener;

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "蓝牙服务已创建");
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.e(TAG, "设备不支持蓝牙");
            return;
        }

        // 注册蓝牙状态广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        try {
            registerReceiver(bluetoothReceiver, filter);
            Log.d(TAG, "蓝牙广播接收器注册成功");
        } catch (Exception e) {
            Log.e(TAG, "注册蓝牙广播接收器失败", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "蓝牙服务已启动");
        // 保持服务运行，即使没有绑定的客户端
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "蓝牙服务正在销毁");
        disconnect();
        try {
            if (bluetoothReceiver != null) {
                unregisterReceiver(bluetoothReceiver);
            }
        } catch (Exception e) {
            Log.e(TAG, "注销广播接收器失败", e);
        }
        Log.d(TAG, "蓝牙服务已销毁");
    }

    // 连接设备
    public void connectToDevice(BluetoothDevice device) {
        Log.d(TAG, "开始连接设备: " + device.getName() + " - " + device.getAddress());

        if (isConnected) {
            Log.d(TAG, "已有连接存在，先断开当前连接");
            disconnect();
        }

        new Thread(() -> {
            try {
                connectSPP(device);
            } catch (Exception e) {
                Log.e(TAG, "连接失败", e);
                if (connectionListener != null) {
                    connectionListener.onBluetoothConnectionFailed(e.getMessage());
                }
            }
        }).start();
    }

    // 断开连接
    public void disconnect() {
        Log.d(TAG, "断开蓝牙连接");
        isConnected = false;
        isReading = false;

        closeSocketSafely();
        connectedDevice = null;

        if (connectionListener != null) {
            connectionListener.onBluetoothDisconnected();
        }
    }

    // 发送数据
    public void sendData(String data) {
        if (!isConnected || outputStream == null) {
            Log.w(TAG, "无法发送数据：未连接或输出流为空");
            return;
        }

        new Thread(() -> {
            try {
                byte[] sendData = data.getBytes();
                outputStream.write(sendData);
                outputStream.flush();
                Log.d(TAG, "数据发送成功: " + data);
            } catch (IOException e) {
                Log.e(TAG, "发送数据失败", e);
                if (connectionListener != null) {
                    connectionListener.onBluetoothConnectionFailed("发送数据失败: " + e.getMessage());
                }
            }
        }).start();
    }

    // 获取连接状态
    public boolean isConnected() {
        return isConnected;
    }

    // 获取连接的设备
    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    // 设置连接监听器
    public void setConnectionListener(BluetoothConnectionListener listener) {
        this.connectionListener = listener;
    }

    // 检查蓝牙权限
    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        }
    }

    // SPP连接方法
    private void connectSPP(BluetoothDevice device) {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "缺少蓝牙权限");
            if (connectionListener != null) {
                connectionListener.onBluetoothConnectionFailed("缺少蓝牙权限");
            }
            return;
        }

        try {
            // 尝试多种连接方式
            boolean connected = false;
            Exception lastException = null;

            // 方法1: 标准UUID连接
            try {
                Log.d(TAG, "尝试标准UUID连接");
                sppSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                // 取消设备发现以加快连接速度
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
                sppSocket.connect();
                connected = true;
                Log.d(TAG, "标准UUID连接成功");
            } catch (Exception e) {
                lastException = e;
                Log.w(TAG, "标准UUID连接失败", e);
                closeSocketSafely();
            }

            // 方法2: 反射方法
            if (!connected) {
                try {
                    Log.d(TAG, "尝试反射连接");
                    Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                    sppSocket = (BluetoothSocket) m.invoke(device, 1);
                    // 取消设备发现以加快连接速度
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    sppSocket.connect();
                    connected = true;
                    Log.d(TAG, "反射连接成功");
                } catch (Exception e) {
                    lastException = e;
                    Log.w(TAG, "反射连接失败", e);
                    closeSocketSafely();
                }
            }

            // 方法3: 不安全连接
            if (!connected) {
                try {
                    Log.d(TAG, "尝试不安全连接");
                    sppSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                    // 取消设备发现以加快连接速度
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    sppSocket.connect();
                    connected = true;
                    Log.d(TAG, "不安全连接成功");
                } catch (Exception e) {
                    lastException = e;
                    Log.w(TAG, "不安全连接失败", e);
                    closeSocketSafely();
                }
            }

            if (connected) {
                inputStream = sppSocket.getInputStream();
                outputStream = sppSocket.getOutputStream();
                isConnected = true;
                connectedDevice = device;

                // 启动数据读取线程
                startDataReadingThread();

                if (connectionListener != null) {
                    connectionListener.onBluetoothConnected(device);
                }

                Log.d(TAG, "蓝牙连接成功: " + device.getName());
            } else {
                String errorMsg = "所有连接方式都失败";
                if (lastException != null) {
                    errorMsg += ": " + lastException.getMessage();
                }
                throw new IOException(errorMsg);
            }

        } catch (Exception e) {
            Log.e(TAG, "连接失败", e);
            closeSocketSafely();
            if (connectionListener != null) {
                connectionListener.onBluetoothConnectionFailed("连接失败: " + e.getMessage());
            }
        }
    }

    // 启动数据读取线程
    private void startDataReadingThread() {
        if (isReading) {
            Log.w(TAG, "数据读取线程已在运行");
            return;
        }

        isReading = true;
        new Thread(() -> {
            Log.d(TAG, "数据读取线程启动");
            byte[] buffer = new byte[1024];
            int bytes;

            try {
                while (isReading && isConnected && sppSocket != null && inputStream != null) {
                    try {
                        bytes = inputStream.read(buffer);
                        if (bytes > 0) {
                            String receivedData = new String(buffer, 0, bytes);
                            Log.d(TAG, "收到数据: " + receivedData);

                            if (connectionListener != null) {
                                connectionListener.onDataReceived(receivedData);
                            }
                        } else if (bytes == -1) {
                            // 流结束
                            Log.w(TAG, "输入流结束");
                            break;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "读取数据失败", e);
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "数据读取线程异常", e);
            } finally {
                isReading = false;
                Log.d(TAG, "数据读取线程结束");
                if (isConnected) {
                    // 连接意外断开
                    Log.w(TAG, "连接意外断开");
                    disconnect();
                }
            }
        }).start();
    }

    // 安全关闭Socket
    private void closeSocketSafely() {
        Log.d(TAG, "安全关闭Socket");
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭输入流出错", e);
        }

        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭输出流出错", e);
        }

        try {
            if (sppSocket != null) {
                sppSocket.close();
                sppSocket = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭Socket出错", e);
        }
    }

    // 蓝牙状态广播接收器
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "收到蓝牙广播: " + action);

            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && connectedDevice != null && device.getAddress().equals(connectedDevice.getAddress())) {
                    Log.d(TAG, "蓝牙连接被断开: " + device.getName());
                    disconnect();
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    Log.d(TAG, "蓝牙已关闭");
                    disconnect();
                } else if (state == BluetoothAdapter.STATE_ON) {
                    Log.d(TAG, "蓝牙已开启");
                }
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String deviceName = device.getName();
                    if (deviceName == null) {
                        deviceName = "未知设备";
                    }
                    Log.d(TAG, "发现设备: " + deviceName + " - " + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "蓝牙搜索开始");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "蓝牙搜索结束");
            }
        }
    };
}
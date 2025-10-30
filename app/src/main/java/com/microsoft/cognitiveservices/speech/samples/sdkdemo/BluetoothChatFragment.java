package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothChatFragment extends Fragment {
    private TextView tvStatus, tvLog;
    private EditText etInput;
    private Button btnSend;
    private BluetoothSocket socket;
    private OutputStream outStream;
    private InputStream inStream;
    private Thread recvThread;
    private volatile boolean running = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
        tvStatus = view.findViewById(R.id.tv_chat_status);
        tvLog = view.findViewById(R.id.tv_chat_log);
        etInput = view.findViewById(R.id.et_chat_input);
        btnSend = view.findViewById(R.id.btn_chat_send);
        btnSend.setOnClickListener(v -> sendMessage());
        updateStatus("未连接蓝牙");
        
        // 如果有传递的设备参数，自动连接
        Bundle args = getArguments();
        if (args != null) {
            BluetoothDevice device = args.getParcelable("device");
            if (device != null) {
                connectToDevice(device);
            }
        }
        
        return view;
    }

    // 外部调用：连接蓝牙设备
    public void connectToDevice(BluetoothDevice device) {
        updateStatus("正在连接 " + device.getName());
        new Thread(() -> {
            try {
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                socket = device.createRfcommSocketToServiceRecord(uuid);
                device.getClass().getMethod("createBond").invoke(device); // 可选：自动配对
                socket.connect();
                outStream = socket.getOutputStream();
                inStream = socket.getInputStream();
                running = true;
                requireActivity().runOnUiThread(() -> {
                    updateStatus("已连接: " + device.getName());
                    Toast.makeText(getContext(), "蓝牙连接成功", Toast.LENGTH_SHORT).show();
                });
                startRecvThread();
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    updateStatus("连接失败: " + device.getName());
                    Toast.makeText(getContext(), "蓝牙连接失败", Toast.LENGTH_SHORT).show();
                });
                e.printStackTrace();
            }
        }).start();
    }

    private void sendMessage() {
        String msg = etInput.getText().toString();
        if (TextUtils.isEmpty(msg) || outStream == null) return;
        try {
            outStream.write(msg.getBytes());
            outStream.flush();
            appendLog("发送: " + msg);
            etInput.setText("");
        } catch (Exception e) {
            Toast.makeText(getContext(), "发送失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void startRecvThread() {
        recvThread = new Thread(() -> {
            byte[] buf = new byte[1024];
            while (running && inStream != null) {
                try {
                    int len = inStream.read(buf);
                    if (len > 0) {
                        String recv = new String(buf, 0, len);
                        requireActivity().runOnUiThread(() -> appendLog("收到: " + recv));
                    }
                } catch (Exception e) {
                    running = false;
                    requireActivity().runOnUiThread(() -> updateStatus("连接断开"));
                    break;
                }
            }
        });
        recvThread.start();
    }

    private void updateStatus(String status) {
        tvStatus.setText(status);
    }

    private void appendLog(String msg) {
        tvLog.append(msg + "\n");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        running = false;
        try {
            if (recvThread != null) recvThread.interrupt();
            if (inStream != null) inStream.close();
            if (outStream != null) outStream.close();
            if (socket != null) socket.close();
        } catch (Exception ignore) {}
    }
}

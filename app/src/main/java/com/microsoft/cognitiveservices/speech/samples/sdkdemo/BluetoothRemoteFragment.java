package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.Set;

public class BluetoothRemoteFragment extends Fragment {

    private BluetoothAdapter bluetoothAdapter;
    private Switch switchBluetooth;
    private Button btnSearch;
    private ListView listPaired, listFound;
    private TextView tvStatus;
    private ArrayAdapter<String> pairedAdapter, foundAdapter;
    private ArrayList<String> pairedList = new ArrayList<>();
    private ArrayList<String> foundList = new ArrayList<>();

    // SPP连接的Socket（经典蓝牙）
    private android.bluetooth.BluetoothSocket sppSocket;

    private final int REQUEST_ENABLE_BT = 1;
    private final int REQUEST_PERMISSION_BT = 2;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bluetooth_remote, container, false);

        // 动态申请蓝牙和定位权限
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[] {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[] {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }
        ArrayList<String> toRequest = new ArrayList<>();
        for (String p : permissions) {
            if (ActivityCompat.checkSelfPermission(requireContext(), p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }
        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), toRequest.toArray(new String[0]), REQUEST_PERMISSION_BT);
        }

        switchBluetooth = view.findViewById(R.id.switch_bluetooth);
        btnSearch = view.findViewById(R.id.btn_search);
        listPaired = view.findViewById(R.id.list_paired);
        listFound = view.findViewById(R.id.list_found);
        tvStatus = view.findViewById(R.id.tv_status);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getContext(), "本设备不支持蓝牙", Toast.LENGTH_LONG).show();
            switchBluetooth.setEnabled(false);
            btnSearch.setEnabled(false);
            return view;
        }

        pairedAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, pairedList);
        foundAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, foundList);
        listPaired.setAdapter(pairedAdapter);
        listFound.setAdapter(foundAdapter);

        // 点击发现设备或已配对设备进行SPP连接
        listFound.setOnItemClickListener((parent, view1, position, id) -> {
            String info = foundList.get(position);
            connectByInfoString(info);
        });
        listPaired.setOnItemClickListener((parent, view12, position, id) -> {
            String info = pairedList.get(position);
            connectByInfoString(info);
        });

        // 蓝牙开关
        switchBluetooth.setChecked(bluetoothAdapter.isEnabled());
        switchBluetooth.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } else {
                bluetoothAdapter.disable();
            }
        });

        // 搜索设备
        btnSearch.setOnClickListener(v -> startDiscovery());

        // 显示已配对设备
        showPairedDevices();

        // 注册广播接收器
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        requireActivity().registerReceiver(receiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        requireActivity().registerReceiver(receiver, filter);

        return view;
    }

    // 通过列表项的“名称\nMAC地址”字符串进行连接
    private void connectByInfoString(String info) {
        try {
            if (info == null) return;
            int idx = info.lastIndexOf('\n');
            if (idx < 0 || info.length() - idx - 1 != 17) {
                Toast.makeText(getContext(), "无法解析设备地址", Toast.LENGTH_SHORT).show();
                return;
            }
            String address = info.substring(idx + 1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(), "缺少蓝牙连接权限，请授予BLUETOOTH_CONNECT", Toast.LENGTH_SHORT).show();
                return;
            }
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            tvStatus.setText("状态：正在连接 " + (device.getName() != null ? device.getName() : address));
            connectSPP(device);
        } catch (Exception e) {
            Toast.makeText(getContext(), "连接发起失败", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void showPairedDevices() {
        pairedList.clear();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedList.add(device.getName() + "\n" + device.getAddress());
            }
        }
        pairedAdapter.notifyDataSetChanged();
    }

    private void startDiscovery() {
        foundList.clear();
        foundAdapter.notifyDataSetChanged();
        tvStatus.setText("状态：正在搜索...");
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    String info = device.getName() + "\n" + device.getAddress();
                    if (!foundList.contains(info)) {
                        foundList.add(info);
                        foundAdapter.notifyDataSetChanged();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                tvStatus.setText("状态：搜索完成");
            }
        }
    };

    // SPP连接方法（在子线程执行）
    private void connectSPP(BluetoothDevice device) {
        new Thread(() -> {
            try {
                // 取消搜索，避免连接变慢
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }

                // 关闭已有连接
                try {
                    if (sppSocket != null && sppSocket.isConnected()) {
                        sppSocket.close();
                    }
                } catch (Exception ignore) {}

                java.util.UUID uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                android.bluetooth.BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid);
                socket.connect();
                sppSocket = socket;

                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("状态：连接成功 " + (device.getName() != null ? device.getName() : device.getAddress()));
                    Toast.makeText(getContext(), "连接成功", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    tvStatus.setText("状态：连接失败 " + (device != null ? (device.getName() != null ? device.getName() : device.getAddress()) : "未知设备"));
                    Toast.makeText(getContext(), "连接失败", Toast.LENGTH_SHORT).show();
                });
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            requireActivity().unregisterReceiver(receiver);
        } catch (Exception e) {
            // ignore
        }
        try {
            if (sppSocket != null) {
                sppSocket.close();
                sppSocket = null;
            }
        } catch (Exception ignore) {}
    }
}

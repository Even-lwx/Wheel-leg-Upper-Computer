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
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
import java.util.Locale;
import java.util.Set;

public class BluetoothRemoteFragment extends Fragment {

    private static final String TAG = "BluetoothRemoteFragment";
    private BluetoothAdapter bluetoothAdapter;
    private Switch switchBluetooth;
    private Button btnSearch, btnDisconnect;
    private ListView listPaired, listFound;
    private TextView tvStatus;

    // 遥控方向按钮
    private Button btnForward, btnBackward, btnLeft, btnRight, btnStop;

    private ArrayAdapter<String> pairedAdapter, foundAdapter;
    private ArrayList<String> pairedList = new ArrayList<>();
    private ArrayList<String> foundList = new ArrayList<>();

    // 蓝牙服务
    private BluetoothService bluetoothService;
    private MainActivity mainActivity;

    // 音频管理器
    private AudioManager audioManager;
    private Handler mainHandler;

    // 状态标志
    private boolean isFragmentActive = false;

    // 遥控命令定义 - 保持为字符串类型以确保兼容性
    private static final String CMD_FORWARD = "1";    // 前进
    private static final String CMD_BACKWARD = "2";   // 后退
    private static final String CMD_LEFT = "4";       // 左转
    private static final String CMD_RIGHT = "5";      // 右转
    private static final String CMD_STOP = "3";       // 停止

    private final int REQUEST_ENABLE_BT = 1;
    private final int REQUEST_PERMISSION_BT = 2;

    // 广播接收器
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        updateUI(() -> {
                            switchBluetooth.setChecked(true);
                            showPairedDevices();
                            tvStatus.setText("状态：蓝牙已开启 🐱");
                        });
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        updateUI(() -> {
                            switchBluetooth.setChecked(false);
                            clearDeviceLists();
                            tvStatus.setText("状态：蓝牙已关闭 😿");
                        });
                        break;
                }
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String deviceName = device.getName();
                    if (deviceName == null) {
                        deviceName = "未知设备";
                    }

                    String info = deviceName + "\n" + device.getAddress();

                    // 使用地址去重
                    boolean exists = false;
                    for (String item : foundList) {
                        if (item.contains(device.getAddress())) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        foundList.add(info);
                        updateUI(() -> {
                            if (foundAdapter != null) {
                                foundAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                updateUI(() -> {
                    int foundCount = foundList.size();
                    String statusText;
                    if (foundCount == 0) {
                        statusText = "状态：没有找到猫友 😿";
                    } else if (foundCount == 1) {
                        statusText = "状态：找到 1 个猫友！🐱";
                    } else {
                        statusText = "状态：找到 " + foundCount + " 个猫友！🎉";
                    }
                    tvStatus.setText(statusText);
                    btnSearch.setEnabled(true);
                    btnSearch.setText("寻找猫友");
                });
            }
        }
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            mainActivity = (MainActivity) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "🐱 BluetoothFragment onCreate");

        // 获取音频管理器
        audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
        mainHandler = new Handler();
        isFragmentActive = true;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = null;
        try {
            view = inflater.inflate(R.layout.fragment_bluetooth_remote, container, false);

            // 初始化UI组件
            initUIComponents(view);

            // 延迟获取蓝牙服务，确保服务已绑定
            new Handler().postDelayed(() -> {
                if (mainActivity != null && mainActivity.isBluetoothServiceBound() && isFragmentActive) {
                    bluetoothService = mainActivity.getBluetoothService();
                    setupBluetoothServiceListener();
                    Log.d(TAG, "蓝牙服务已绑定");
                } else {
                    Log.w(TAG, "蓝牙服务未绑定或Fragment已销毁");
                }
            }, 500);

            // 检查权限
            checkAndRequestPermissions();

        } catch (Exception e) {
            Log.e(TAG, "创建视图时发生错误", e);
            Toast.makeText(getContext(), "初始化界面失败", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "🐱 BluetoothFragment onResume");
        isFragmentActive = true;

        // 延迟注册蓝牙广播接收器
        new Handler().postDelayed(() -> {
            if (isFragmentActive) {
                registerBluetoothReceiver();
            }
        }, 300);

        // 延迟请求音频焦点，确保蓝牙音频优先
        new Handler().postDelayed(() -> {
            if (audioManager != null && isFragmentActive) {
                int result = audioManager.requestAudioFocus(
                        new AudioManager.OnAudioFocusChangeListener() {
                            @Override
                            public void onAudioFocusChange(int focusChange) {
                                switch (focusChange) {
                                    case AudioManager.AUDIOFOCUS_GAIN:
                                        Log.d(TAG, "重新获得音频焦点");
                                        break;
                                    case AudioManager.AUDIOFOCUS_LOSS:
                                        Log.w(TAG, "失去音频焦点");
                                        break;
                                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                                        Log.w(TAG, "暂时失去音频焦点");
                                        break;
                                }
                            }
                        },
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                );

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d(TAG, "成功获得音频焦点");
                } else {
                    Log.w(TAG, "无法获得音频焦点");
                }
            }
        }, 500);

        // 恢复蓝牙状态显示
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled() && isFragmentActive) {
            showPairedDevices();
        }

        // 更新连接状态显示
        updateConnectionStatus();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "🐱 BluetoothFragment onPause");
        isFragmentActive = false;

        // 安全放弃音频焦点
        if (audioManager != null) {
            try {
                audioManager.abandonAudioFocus(null);
            } catch (Exception e) {
                Log.e(TAG, "放弃音频焦点失败", e);
            }
        }

        // 停止设备搜索但不关闭连接
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        // 注销广播接收器但保持连接
        try {
            if (getActivity() != null) {
                getActivity().unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            Log.w(TAG, "注销广播接收器失败（可能未注册）", e);
        }
    }

    private void initUIComponents(View view) {
        try {
            // 蓝牙控制相关组件
            switchBluetooth = view.findViewById(R.id.switch_bluetooth);
            btnSearch = view.findViewById(R.id.btn_search);
            btnDisconnect = view.findViewById(R.id.btn_disconnect);
            listPaired = view.findViewById(R.id.list_paired);
            listFound = view.findViewById(R.id.list_found);
            tvStatus = view.findViewById(R.id.tv_status);

            // 遥控方向按钮
            btnForward = view.findViewById(R.id.btn_forward);
            btnBackward = view.findViewById(R.id.btn_backward);
            btnLeft = view.findViewById(R.id.btn_left);
            btnRight = view.findViewById(R.id.btn_right);
            btnStop = view.findViewById(R.id.btn_stop);

            // 初始化适配器
            pairedAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, pairedList) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView textView = view.findViewById(android.R.id.text1);
                    textView.setPadding(16, 16, 16, 16);
                    textView.setTextSize(14);
                    return view;
                }
            };

            foundAdapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, foundList) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView textView = view.findViewById(android.R.id.text1);
                    textView.setPadding(16, 16, 16, 16);
                    textView.setTextSize(14);
                    return view;
                }
            };

            listPaired.setAdapter(pairedAdapter);
            listFound.setAdapter(foundAdapter);

            // 初始隐藏断开连接按钮
            btnDisconnect.setVisibility(View.GONE);

            // 设置事件监听器
            setupEventListeners();

        } catch (Exception e) {
            Log.e(TAG, "初始化UI组件失败", e);
            Toast.makeText(getContext(), "初始化UI失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBluetoothServiceListener() {
        if (bluetoothService != null && isFragmentActive) {
            bluetoothService.setConnectionListener(new BluetoothService.BluetoothConnectionListener() {
                @Override
                public void onBluetoothConnected(BluetoothDevice device) {
                    updateUI(() -> {
                        String deviceName = getDeviceName(device);
                        tvStatus.setText("状态：已连接到 " + deviceName + " 🎉");
                        Toast.makeText(getContext(), "🐱 连接成功！找到猫友啦~", Toast.LENGTH_SHORT).show();
                        btnDisconnect.setVisibility(View.VISIBLE);
                    });
                }

                @Override
                public void onBluetoothDisconnected() {
                    updateUI(() -> {
                        handleConnectionLost();
                    });
                }

                @Override
                public void onBluetoothConnectionFailed(String error) {
                    updateUI(() -> {
                        tvStatus.setText("状态：连接失败 😿");
                        Toast.makeText(getContext(), "😿 连接失败: " + error, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onDataReceived(String data) {
                    // 由于移除了数据传输界面，这里只记录日志
                    Log.d(TAG, "收到蓝牙数据: " + data);
                }
            });
        }
    }

    private void checkAndRequestPermissions() {
        if (!isFragmentActive) return;

        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
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
        } else {
            // 权限已授予，初始化蓝牙
            initializeBluetooth();
        }
    }

    private void initializeBluetooth() {
        if (!isFragmentActive) return;

        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                updateUI(() -> {
                    Toast.makeText(getContext(), "😿 本设备不支持蓝牙", Toast.LENGTH_LONG).show();
                    if (switchBluetooth != null) {
                        switchBluetooth.setEnabled(false);
                    }
                    if (btnSearch != null) {
                        btnSearch.setEnabled(false);
                    }
                });
                return;
            }

            // 设置蓝牙开关状态
            if (switchBluetooth != null) {
                switchBluetooth.setChecked(bluetoothAdapter.isEnabled());
            }

            // 显示已配对设备
            if (bluetoothAdapter.isEnabled()) {
                showPairedDevices();
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化蓝牙失败", e);
            updateUI(() -> {
                Toast.makeText(getContext(), "初始化蓝牙失败", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void registerBluetoothReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            requireActivity().registerReceiver(receiver, filter);
            Log.d(TAG, "蓝牙广播接收器注册成功");
        } catch (Exception e) {
            Log.e(TAG, "注册蓝牙广播接收器失败", e);
        }
    }

    private void setupEventListeners() {
        try {
            // 蓝牙开关
            if (switchBluetooth != null) {
                switchBluetooth.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        enableBluetooth();
                    } else {
                        disableBluetooth();
                    }
                });
            }

            // 搜索设备按钮
            if (btnSearch != null) {
                btnSearch.setOnClickListener(v -> {
                    if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                        btnSearch.setText("寻找猫友");
                        tvStatus.setText("状态：已取消搜索");
                    } else {
                        startDiscovery();
                    }
                });
            }

            // 断开连接按钮
            if (btnDisconnect != null) {
                btnDisconnect.setOnClickListener(v -> disconnectDevice());
            }

            // 设备列表点击事件
            if (listFound != null) {
                listFound.setOnItemClickListener((parent, view1, position, id) -> {
                    if (position < foundList.size()) {
                        String info = foundList.get(position);
                        connectByInfoString(info);
                    }
                });
            }

            if (listPaired != null) {
                listPaired.setOnItemClickListener((parent, view12, position, id) -> {
                    if (position < pairedList.size()) {
                        String info = pairedList.get(position);
                        connectByInfoString(info);
                    }
                });
            }

            // 遥控方向按钮监听器
            setupRemoteControlListeners();
        } catch (Exception e) {
            Log.e(TAG, "设置事件监听器失败", e);
        }
    }

    // 设置遥控方向按钮监听器
    private void setupRemoteControlListeners() {
        try {
            // 前进按钮
            if (btnForward != null) {
                btnForward.setOnClickListener(v -> sendRemoteCommand(CMD_FORWARD, "前进"));
            }

            // 后退按钮
            if (btnBackward != null) {
                btnBackward.setOnClickListener(v -> sendRemoteCommand(CMD_BACKWARD, "后退"));
            }

            // 左转按钮
            if (btnLeft != null) {
                btnLeft.setOnClickListener(v -> sendRemoteCommand(CMD_LEFT, "左转"));
            }

            // 右转按钮
            if (btnRight != null) {
                btnRight.setOnClickListener(v -> sendRemoteCommand(CMD_RIGHT, "右转"));
            }

            // 停止按钮
            if (btnStop != null) {
                btnStop.setOnClickListener(v -> sendRemoteCommand(CMD_STOP, "停止"));
            }
        } catch (Exception e) {
            Log.e(TAG, "设置遥控监听器失败", e);
        }
    }

    // 发送遥控命令 - 修正参数类型
    private void sendRemoteCommand(String command, String description) {
        if (bluetoothService == null || !bluetoothService.isConnected() || !isFragmentActive) {
            updateUI(() -> Toast.makeText(getContext(), "😿 未连接设备", Toast.LENGTH_SHORT).show());
            return;
        }

        // 使用蓝牙服务发送数据
        bluetoothService.sendData(command);
        Log.d(TAG, "发送遥控命令: " + command + " - " + description);

        updateUI(() -> {
            Toast.makeText(getContext(), "🐱 发送: " + description, Toast.LENGTH_SHORT).show();
        });
    }

    private void enableBluetooth() {
        Log.d(TAG, "🐱 尝试开启蓝牙...");
        if (bluetoothAdapter == null) {
            initializeBluetooth();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            if (hasBluetoothPermission()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                updateUI(() -> {
                    if (switchBluetooth != null) {
                        switchBluetooth.setChecked(false);
                    }
                });
                checkAndRequestPermissions();
            }
        }
    }

    private void disableBluetooth() {
        Log.d(TAG, "🐱 尝试关闭蓝牙...");
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            if (hasBluetoothPermission()) {
                // 先断开所有连接
                disconnectDevice();
                // 然后关闭蓝牙
                bluetoothAdapter.disable();
            } else {
                updateUI(() -> {
                    if (switchBluetooth != null) {
                        switchBluetooth.setChecked(true);
                    }
                });
                checkAndRequestPermissions();
            }
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void startDiscovery() {
        Log.d(TAG, "🐾 开始寻找猫友...");
        if (bluetoothAdapter == null) {
            updateUI(() -> Toast.makeText(getContext(), "蓝牙适配器未初始化", Toast.LENGTH_SHORT).show());
            return;
        }

        if (!hasDiscoveryPermission()) {
            updateUI(() -> Toast.makeText(getContext(), "需要蓝牙搜索权限", Toast.LENGTH_SHORT).show());
            checkAndRequestPermissions();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            updateUI(() -> Toast.makeText(getContext(), "请先开启蓝牙", Toast.LENGTH_SHORT).show());
            updateUI(() -> {
                if (switchBluetooth != null) {
                    switchBluetooth.setChecked(true);
                }
            });
            return;
        }

        foundList.clear();
        if (foundAdapter != null) {
            foundAdapter.notifyDataSetChanged();
        }

        updateUI(() -> {
            tvStatus.setText("状态：正在寻找猫友... 🐾");
            btnSearch.setText("取消搜索");
        });

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        // 修复搜索问题：确保有足够的权限和时间进行搜索
        boolean started = bluetoothAdapter.startDiscovery();
        if (!started) {
            updateUI(() -> {
                tvStatus.setText("状态：搜索启动失败 😿");
                btnSearch.setText("寻找猫友");
                Toast.makeText(getContext(), "😿 搜索启动失败，请检查权限", Toast.LENGTH_SHORT).show();
            });
        } else {
            Log.d(TAG, "蓝牙搜索已启动");
            // 设置搜索超时时间
            new Handler().postDelayed(() -> {
                if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                    updateUI(() -> {
                        tvStatus.setText("状态：搜索完成");
                        btnSearch.setText("寻找猫友");
                    });
                }
            }, 10000); // 10秒后自动停止搜索
        }
    }

    private boolean hasDiscoveryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void connectByInfoString(String info) {
        try {
            if (info == null) return;

            // 查找MAC地址（通常是最后17个字符）
            String[] parts = info.split("\n");
            String address = null;

            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i].trim();
                if (part.matches("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")) {
                    address = part;
                    break;
                }
            }

            if (address == null) {
                updateUI(() -> Toast.makeText(getContext(), "无法解析设备地址", Toast.LENGTH_SHORT).show());
                return;
            }

            if (!hasBluetoothPermission()) {
                updateUI(() -> Toast.makeText(getContext(), "需要蓝牙权限才能连接设备", Toast.LENGTH_SHORT).show());
                checkAndRequestPermissions();
                return;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            if (device == null) {
                updateUI(() -> Toast.makeText(getContext(), "无法获取设备信息", Toast.LENGTH_SHORT).show());
                return;
            }

            updateUI(() -> tvStatus.setText("状态：正在连接 " + getDeviceName(device)));

            // 使用蓝牙服务进行连接
            if (bluetoothService != null) {
                bluetoothService.connectToDevice(device);
            } else {
                updateUI(() -> Toast.makeText(getContext(), "蓝牙服务未就绪", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            Log.e(TAG, "连接发起失败", e);
            updateUI(() -> Toast.makeText(getContext(), "连接发起失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void showPairedDevices() {
        if (bluetoothAdapter == null || !hasBluetoothPermission() || !isFragmentActive) {
            return;
        }

        pairedList.clear();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedList.add(getDeviceName(device) + "\n" + device.getAddress());
            }
        } else {
            pairedList.add("没有已配对的设备");
        }
        updateUI(() -> {
            if (pairedAdapter != null) {
                pairedAdapter.notifyDataSetChanged();
            }
        });
    }

    private String getDeviceName(BluetoothDevice device) {
        if (device == null) return "未知设备";

        String name = device.getName();
        if (name == null || name.trim().isEmpty()) {
            return "未知设备";
        }
        return name;
    }

    private void clearDeviceLists() {
        pairedList.clear();
        foundList.clear();
        updateUI(() -> {
            if (pairedAdapter != null) pairedAdapter.notifyDataSetChanged();
            if (foundAdapter != null) foundAdapter.notifyDataSetChanged();
        });
    }

    // 处理连接丢失
    private void handleConnectionLost() {
        updateUI(() -> {
            try {
                tvStatus.setText("状态：连接已断开 😿");
                btnDisconnect.setVisibility(View.GONE);
                Toast.makeText(getContext(), "😿 连接已断开", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "处理连接丢失失败", e);
            }
        });
    }

    // 添加连接状态更新方法
    private void updateConnectionStatus() {
        updateUI(() -> {
            try {
                if (bluetoothService != null && bluetoothService.isConnected()) {
                    BluetoothDevice device = bluetoothService.getConnectedDevice();
                    if (device != null) {
                        tvStatus.setText("状态：已连接到 " + getDeviceName(device));
                        btnDisconnect.setVisibility(View.VISIBLE);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "更新连接状态失败", e);
            }
        });
    }

    private void disconnectDevice() {
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
    }

    // UI线程安全更新
    private void updateUI(Runnable action) {
        if (getActivity() != null && !getActivity().isFinishing() && isAdded() && isFragmentActive) {
            getActivity().runOnUiThread(() -> {
                if (isAdded() && isVisible() && isFragmentActive) {
                    try {
                        action.run();
                    } catch (Exception e) {
                        Log.e(TAG, "UI更新失败", e);
                    }
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_BT && isFragmentActive) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                updateUI(() -> {
                    Toast.makeText(getContext(), "🐱 权限已授予", Toast.LENGTH_SHORT).show();
                    initializeBluetooth();
                });
            } else {
                updateUI(() -> {
                    Toast.makeText(getContext(), "😿 部分权限未授予，功能可能受限", Toast.LENGTH_SHORT).show();
                    if (bluetoothAdapter != null && switchBluetooth != null) {
                        switchBluetooth.setChecked(bluetoothAdapter.isEnabled());
                    }
                });
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && isFragmentActive) {
            if (resultCode == Activity.RESULT_OK) {
                updateUI(() -> {
                    Toast.makeText(getContext(), "🐱 蓝牙已开启", Toast.LENGTH_SHORT).show();
                    showPairedDevices();
                });
            } else {
                updateUI(() -> {
                    if (switchBluetooth != null) {
                        switchBluetooth.setChecked(false);
                    }
                    Toast.makeText(getContext(), "😿 用户拒绝开启蓝牙", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "🐱 BluetoothFragment onDestroyView");
        isFragmentActive = false;

        try {
            // 停止设备搜索
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }

            // 注销广播接收器
            try {
                if (getActivity() != null) {
                    getActivity().unregisterReceiver(receiver);
                }
            } catch (Exception e) {
                Log.w(TAG, "注销广播接收器失败（可能已注销）", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "资源释放失败", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🐱 BluetoothFragment onDestroy");
        isFragmentActive = false;
    }
}
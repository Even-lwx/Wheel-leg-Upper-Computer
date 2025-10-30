package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import android.os.Vibrator;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.KeywordRecognitionModel;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.Connection;
import com.microsoft.cognitiveservices.speech.AudioDataStream;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// 缺少这行导入
import android.content.pm.PackageManager;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;

public class MainActivity extends AppCompatActivity {
    private static final String logTag = "MainActivity";
    private SpeakingRunnable speakingRunnable;
    private ExecutorService singleThreadExecutor;
    private SpeechSynthesizer synthesizer;
    private Connection connection;
    private AudioTrack audioTrack;
    private final Object synchronizedObj;
    private boolean stopped = false;

    // 蓝牙服务相关
    private BluetoothService bluetoothService;
    private boolean isBluetoothServiceBound = false;

    // 震动器
    private Vibrator vibrator;

    // 添加蓝牙状态跟踪变量
    private boolean isBluetoothFragmentActive = false;
    private boolean isVoiceInitialized = false;
    private boolean isVoiceFunctionsPaused = false;

    // 添加音频焦点状态
    private boolean hasAudioFocus = false;

    // 横屏相关
    private boolean isLandscapeMode = false;
    private ImageView landscapeEyeAnimation;
    private View fragmentContainer;
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigationView;

    // 添加蓝牙连接状态监听器
    public interface BluetoothConnectionListener {
        void onBluetoothFragmentResumed();
        void onBluetoothFragmentPaused();
    }
    private BluetoothConnectionListener bluetoothConnectionListener;

    public void setBluetoothConnectionListener(BluetoothConnectionListener listener) {
        this.bluetoothConnectionListener = listener;
    }

    //
    // Configuration for speech recognition
    //

    // Replace below with your own subscription key
    private static final String SpeechSubscriptionKey = "4zJKGra9pMHLReCDqpyaCROK7LO6wT9Bcx56tmc8LBtuVHj5H6r8JQQJ99BFACYeBjFXJ3w3AAAYACOGNOWg";

    private static final String SpeechRegion = "eastus";
    final SpeechConfig speechConfig=SpeechConfig.fromSubscription(SpeechSubscriptionKey, SpeechRegion);
    static int no_reg_count=0;
    //
    // Configuration for intent recognition
    //


    // Replace below with your own Keyword model file, kws.table model file is configured for "Computer" keyword
    private static final String KwsModelFile = "nihao.table";
    private KeywordRecognitionModel kwsModel;

    // 蓝牙遥控命令定义（与 BluetoothRemoteFragment 保持一致）
    private static final String CMD_FORWARD = "1";    // 前进
    private static final String CMD_BACKWARD = "2";   // 后退
    private static final String CMD_LEFT = "4";       // 左转
    private static final String CMD_RIGHT = "5";      // 右转
    private static final String CMD_STOP = "3";       // 停止

    private final Object lock = new Object();
    private Future<SpeechRecognitionResult> task;

    private MicrophoneStream microphoneStream;

    // 蓝牙服务连接
    private ServiceConnection bluetoothServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(logTag, "蓝牙服务已连接");
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            bluetoothService = binder.getService();
            isBluetoothServiceBound = true;

            // 设置蓝牙连接监听器
            bluetoothService.setConnectionListener(new BluetoothService.BluetoothConnectionListener() {
                @Override
                public void onBluetoothConnected(android.bluetooth.BluetoothDevice device) {
                    Log.i(logTag, "蓝牙连接成功: " + device.getName());
                    // 可以在这里更新全局状态或通知Fragment
                }

                @Override
                public void onBluetoothDisconnected() {
                    Log.i(logTag, "蓝牙连接断开");
                    // 可以在这里更新全局状态或通知Fragment
                }

                @Override
                public void onBluetoothConnectionFailed(String error) {
                    Log.e(logTag, "蓝牙连接失败: " + error);
                }

                @Override
                public void onDataReceived(String data) {
                    Log.i(logTag, "收到蓝牙数据: " + data);
                    // 处理接收到的数据
                }
            });

            Log.d(logTag, "蓝牙服务已绑定并监听器已设置");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(logTag, "蓝牙服务已断开连接");
            isBluetoothServiceBound = false;
            bluetoothService = null;
        }
    };

    public MainActivity() {
        synchronizedObj = new Object();
    }

    private MicrophoneStream createMicrophoneStream() {
        this.releaseMicrophoneStream();

        microphoneStream = new MicrophoneStream();
        return microphoneStream;
    }

    private void releaseMicrophoneStream() {
        if (microphoneStream != null) {
            try {
                microphoneStream.close();
            } catch (Exception e) {
                Log.e(logTag, "关闭麦克风流时出错: " + e.getMessage());
            }
            microphoneStream = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(logTag, "MainActivity onCreate");

        // 获取布局组件引用
        fragmentContainer = findViewById(R.id.fragment_container);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // 初始化震动器
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // 检查当前屏幕方向
        checkOrientation();

        // 首先检查并请求必要的权限
        checkAndRequestPermissions();

        // 延迟启动并绑定蓝牙服务，确保权限已获取
        new Handler().postDelayed(() -> {
            try {
                Intent serviceIntent = new Intent(this, BluetoothService.class);
                startService(serviceIntent);
                bindService(serviceIntent, bluetoothServiceConnection, Context.BIND_AUTO_CREATE);
                Log.d(logTag, "蓝牙服务已启动并绑定");
            } catch (Exception e) {
                Log.e(logTag, "启动蓝牙服务失败: " + e.getMessage());
            }
        }, 1000);

        // 默认显示语音助手Fragment
        try {
            replaceFragment(new PlaceholderFragment());
            Log.d(logTag, "默认Fragment已设置");
        } catch (Exception e) {
            Log.e(logTag, "设置默认Fragment失败: " + e.getMessage());
        }

        // 底部导航栏点击事件
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            try {
                if (item.getItemId() == R.id.navigation_bluetooth) {
                    fragment = new BluetoothRemoteFragment();
                    isBluetoothFragmentActive = true;
                    // 切换到蓝牙时暂停语音功能
                    pauseVoiceFunctions();
                    if (bluetoothConnectionListener != null) {
                        bluetoothConnectionListener.onBluetoothFragmentResumed();
                    }
                    Log.d(logTag, "切换到蓝牙Fragment");
                } else if (item.getItemId() == R.id.navigation_voice_assistant) {
                    fragment = new PlaceholderFragment();
                    isBluetoothFragmentActive = false;
                    // 切换到语音时恢复语音功能
                    resumeVoiceFunctions();
                    if (bluetoothConnectionListener != null) {
                        bluetoothConnectionListener.onBluetoothFragmentPaused();
                    }
                    Log.d(logTag, "切换到语音Fragment");
                }
                if (fragment != null) {
                    replaceFragment(fragment);
                    return true;
                }
            } catch (Exception e) {
                Log.e(logTag, "切换Fragment失败: " + e.getMessage());
            }
            return false;
        });
    }

    // 检测屏幕方向
    private void checkOrientation() {
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            switchToLandscapeMode();
        } else {
            switchToPortraitMode();
        }
    }

    // 切换到横屏模式
    private void switchToLandscapeMode() {
        if (isLandscapeMode) return;
        
        Log.d(logTag, "切换到横屏沉浸式模式");
        isLandscapeMode = true;
        
        // 隐藏系统UI实现沉浸式体验
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        
        // 隐藏ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // 隐藏底部导航栏和Fragment容器
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(View.GONE);
        }
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.GONE);
        }
        
        // 加载横屏布局的眼睛动画
        setContentView(R.layout.activity_main_landscape);
        landscapeEyeAnimation = findViewById(R.id.landscape_eye_animation);
        
        // 使用Glide加载GIF动画
        try {
            com.bumptech.glide.Glide.with(this)
                    .asGif()
                    .load(R.drawable.welcome_animation)
                    .into(landscapeEyeAnimation);
            Log.d(logTag, "横屏眼睛动画已加载");
        } catch (Exception e) {
            Log.e(logTag, "加载横屏动画失败: " + e.getMessage());
        }
        
        // 后台服务(语音、蓝牙)继续运行，不做任何改变
        Log.d(logTag, "后台服务继续运行");
    }

    // 切换到竖屏模式
    private void switchToPortraitMode() {
        if (!isLandscapeMode) return;
        
        Log.d(logTag, "切换回竖屏正常模式");
        isLandscapeMode = false;
        
        // 步骤1: 清除所有系统UI标志
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        
        // 步骤2: 清除所有Window标志
        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN 
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        );
        
        // 步骤3: 强制设置为非全屏模式
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        
        // 步骤4: 先恢复ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().show();
        }
        
        // 步骤5: 恢复正常布局
        setContentView(R.layout.activity_main);
        
        // 步骤6: 等待布局完成后再刷新
        getWindow().getDecorView().post(() -> {
            getWindow().getDecorView().requestLayout();
        });
        
        // 重新获取组件引用
        fragmentContainer = findViewById(R.id.fragment_container);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        
        // 显示ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().show();
        }
        
        // 恢复Fragment和导航栏
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(View.VISIBLE);
            
            // 重新设置导航栏监听器
            bottomNavigationView.setOnItemSelectedListener(item -> {
                Fragment fragment = null;
                try {
                    if (item.getItemId() == R.id.navigation_bluetooth) {
                        fragment = new BluetoothRemoteFragment();
                        isBluetoothFragmentActive = true;
                        pauseVoiceFunctions();
                        if (bluetoothConnectionListener != null) {
                            bluetoothConnectionListener.onBluetoothFragmentResumed();
                        }
                        Log.d(logTag, "切换到蓝牙Fragment");
                    } else if (item.getItemId() == R.id.navigation_voice_assistant) {
                        fragment = new PlaceholderFragment();
                        isBluetoothFragmentActive = false;
                        resumeVoiceFunctions();
                        if (bluetoothConnectionListener != null) {
                            bluetoothConnectionListener.onBluetoothFragmentPaused();
                        }
                        Log.d(logTag, "切换到语音Fragment");
                    }
                    if (fragment != null) {
                        replaceFragment(fragment);
                        return true;
                    }
                } catch (Exception e) {
                    Log.e(logTag, "切换Fragment失败: " + e.getMessage());
                }
                return false;
            });
            
            // 恢复当前显示的Fragment
            try {
                if (isBluetoothFragmentActive) {
                    replaceFragment(new BluetoothRemoteFragment());
                } else {
                    replaceFragment(new PlaceholderFragment());
                }
            } catch (Exception e) {
                Log.e(logTag, "恢复Fragment失败: " + e.getMessage());
            }
        }
        
        if (fragmentContainer != null) {
            fragmentContainer.setVisibility(View.VISIBLE);
        }
        
        Log.d(logTag, "竖屏正常模式已恢复");
    }

    // 监听屏幕方向变化
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(logTag, "屏幕方向改变: " + newConfig.orientation);
        checkOrientation();
    }

    // 检查并请求权限
    private void checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    RECORD_AUDIO,
                    INTERNET,
                    READ_EXTERNAL_STORAGE
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    RECORD_AUDIO,
                    INTERNET,
                    READ_EXTERNAL_STORAGE
            };
        }

        ArrayList<String> toRequest = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }

        if (!toRequest.isEmpty()) {
            Log.d(logTag, "请求权限: " + toRequest);
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), 100);
        } else {
            Log.d(logTag, "所有必要权限已授予");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(logTag, "权限请求结果: requestCode=" + requestCode);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            Log.d(logTag, "所有权限已授予");
            // 权限授予后初始化语音功能
            if (!isVoiceInitialized) {
                initializeVoiceSDK();
            }
        } else {
            Log.w(logTag, "部分权限未授予");
            // 可以在这里提示用户需要权限
        }
    }

    private void replaceFragment(Fragment fragment) {
        try {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            transaction.commitNow(); // 使用commitNow避免异步问题
            Log.d(logTag, "Fragment替换成功");
        } catch (Exception e) {
            Log.e(logTag, "替换Fragment时出错: " + e.getMessage());
            // 尝试使用commit
            try {
                FragmentManager fragmentManager = getSupportFragmentManager();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                transaction.replace(R.id.fragment_container, fragment);
                transaction.commit();
            } catch (Exception e2) {
                Log.e(logTag, "使用commit也失败: " + e2.getMessage());
            }
        }
    }

    private void pauseVoiceFunctions() {
        if (isVoiceFunctionsPaused) {
            return; // 已经暂停过了，避免重复操作
        }

        Log.i(logTag, "暂停语音功能，保护蓝牙连接");
        isVoiceFunctionsPaused = true;

        try {
            // 停止语音播放
            if (speakingRunnable != null) {
                speakingRunnable.stop(null);
            }

            // 释放麦克风资源
            releaseMicrophoneStream();

            // 停止语音识别任务
            if (task != null && !task.isDone()) {
                task.cancel(true);
                Log.i(logTag, "语音识别任务已取消");
            }

            // 安全暂停音频轨道
            if (audioTrack != null) {
                try {
                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.pause();
                        Log.i(logTag, "音频轨道已暂停");
                    }
                    // 不要flush，避免数据丢失
                } catch (Exception e) {
                    Log.e(logTag, "暂停音频轨道失败: " + e.getMessage());
                }
            }

            // 延迟放弃音频焦点
            new Handler().postDelayed(() -> {
                try {
                    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (audioManager != null && hasAudioFocus) {
                        int result = audioManager.abandonAudioFocus(null);
                        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            hasAudioFocus = false;
                            Log.i(logTag, "音频焦点已放弃");
                        }
                    }
                } catch (Exception e) {
                    Log.e(logTag, "放弃音频焦点失败: " + e.getMessage());
                }
            }, 500);

        } catch (Exception e) {
            Log.e(logTag, "暂停语音功能时出错: " + e.getMessage());
        }
    }

    private void resumeVoiceFunctions() {
        Log.i(logTag, "恢复语音功能，检查蓝牙状态");
        isVoiceFunctionsPaused = false;

        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                Log.w(logTag, "AudioManager 不可用");
                return;
            }

            // 检查当前是否有蓝牙音频连接
            if (isBluetoothAudioActive(audioManager)) {
                Log.i(logTag, "检测到蓝牙音频活跃，延迟语音功能恢复");
                // 延迟初始化，等待蓝牙音频稳定
                new Handler().postDelayed(() -> {
                    if (!isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                        safelyInitializeVoiceFunctions();
                    }
                }, 1500);
            } else {
                safelyInitializeVoiceFunctions();
            }

        } catch (Exception e) {
            Log.e(logTag, "恢复语音功能时出错: " + e.getMessage());
        }
    }

    // 检查蓝牙音频是否活跃
    private boolean isBluetoothAudioActive(AudioManager audioManager) {
        try {
            return audioManager.isBluetoothA2dpOn() ||
                    audioManager.isBluetoothScoOn() ||
                    isBluetoothHeadsetConnected(audioManager);
        } catch (Exception e) {
            Log.w(logTag, "检查蓝牙音频状态失败: " + e.getMessage());
            return false;
        }
    }

    // 检查蓝牙耳机是否连接
    private boolean isBluetoothHeadsetConnected(AudioManager audioManager) {
        try {
            return audioManager.isBluetoothScoAvailableOffCall() ||
                    audioManager.isWiredHeadsetOn();
        } catch (Exception e) {
            Log.w(logTag, "检查蓝牙设备连接状态失败: " + e.getMessage());
            return false;
        }
    }

    // 安全的语音功能初始化
    private void safelyInitializeVoiceFunctions() {
        if (!isVoiceInitialized) {
            initializeVoiceSDK();
        } else {
            Log.i(logTag, "安全恢复语音功能");
            try {
                // 只有在没有蓝牙连接且不在蓝牙Fragment时才请求音频焦点
                if (!isBluetoothFragmentActive && (bluetoothService == null || !bluetoothService.isConnected())) {
                    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (audioManager != null && !hasAudioFocus) {
                        int result = audioManager.requestAudioFocus(
                                null,
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                        );

                        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            hasAudioFocus = true;
                            Log.i(logTag, "音频焦点请求成功");

                            // 延迟恢复音频轨道
                            new Handler().postDelayed(() -> {
                                if (audioTrack != null && !isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                                    try {
                                        if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                                            audioTrack.play();
                                            Log.i(logTag, "音频轨道重新启动");
                                        }
                                    } catch (Exception e) {
                                        Log.e(logTag, "启动音频轨道失败: " + e.getMessage());
                                    }
                                }
                            }, 300);

                            // 延迟开始关键词识别
                            new Handler().postDelayed(() -> {
                                if (kwsModel != null && !isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                                    try {
                                        RegnizeKeyword(speechConfig, kwsModel);
                                        Log.i(logTag, "关键词识别重新启动");
                                    } catch (Exception e) {
                                        Log.e(logTag, "启动关键词识别失败: " + e.getMessage());
                                    }
                                }
                            }, 800);
                        }
                    }
                } else {
                    Log.i(logTag, "检测到蓝牙连接或蓝牙Fragment活跃，跳过语音功能恢复");
                }
            } catch (Exception e) {
                Log.e(logTag, "安全恢复语音功能时出错: " + e.getMessage());
            }
        }
    }

    private void initializeVoiceSDK() {
        Log.i(logTag, "初始化语音SDK");
        try {
            // 延迟初始化，避免立即干扰蓝牙
            new Handler().postDelayed(() -> {
                try {
                    kwsModel = KeywordRecognitionModel.fromFile(copyAssetToCacheAndGetFilePath(KwsModelFile));
                    singleThreadExecutor = Executors.newSingleThreadExecutor();

                    // 改进的音频轨道配置，使用更兼容的设置
                    int bufferSize = AudioTrack.getMinBufferSize(
                            24000,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);

                    // 确保之前的audioTrack被释放
                    if (audioTrack != null) {
                        try {
                            audioTrack.release();
                        } catch (Exception e) {
                            Log.w(logTag, "释放旧audioTrack时出错: " + e.getMessage());
                        }
                    }

                    audioTrack = new AudioTrack(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build(),
                            new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(24000)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build(),
                            bufferSize * 2, // 使用双倍缓冲区
                            AudioTrack.MODE_STREAM,
                            AudioManager.AUDIO_SESSION_ID_GENERATE);

                    // 配置语音参数
                    speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm);
                    speechConfig.setSpeechRecognitionLanguage("zh-CN");
                    speechConfig.setSpeechSynthesisLanguage("zh-CN");
                    speechConfig.setSpeechSynthesisVoiceName("zh-CN-XiaoxiaoMultilingualNeural");

                    // 初始化语音合成器
                    if (synthesizer != null) {
                        try {
                            synthesizer.close();
                        } catch (Exception e) {
                            Log.w(logTag, "关闭旧synthesizer时出错: " + e.getMessage());
                        }
                    }

                    synthesizer = new SpeechSynthesizer(speechConfig, null);

                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (Exception e) {
                            Log.w(logTag, "关闭旧connection时出错: " + e.getMessage());
                        }
                    }
                    connection = Connection.fromSpeechSynthesizer(synthesizer);
                    connection.openConnection(true);

                    // 设置合成完成事件监听
                    synthesizer.SynthesisCompleted.addEventListener((o, e) -> {
                        Log.i(logTag, "Synthesis finished.");
                        Log.i(logTag, e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_SynthesisFirstByteLatencyMs) + " ms.");
                        Log.i(logTag, e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_SynthesisFinishLatencyMs) + " ms.");
                        e.close();
                    });

                    isVoiceInitialized = true;

                    // 请求音频焦点
                    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (audioManager != null) {
                        int result = audioManager.requestAudioFocus(
                                null,
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                        );
                        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            hasAudioFocus = true;
                        }
                    }

                    // 延迟开始欢迎语音
                    new Handler().postDelayed(() -> {
                        if (!isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                            String hello = "您好，很高兴为您服务，您可以唤醒我说：你好！";
                            speak(hello, () -> {
                                if (!isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                                    RegnizeKeyword(speechConfig, kwsModel);
                                }
                            });
                        }
                    }, 1000);

                } catch (Exception ex) {
                    Log.e(logTag, "初始化语音SDK失败: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }, 500); // 延迟500毫秒初始化
        } catch (Exception ex) {
            Log.e(logTag, "初始化语音SDK失败: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void RegnizeKeyword(SpeechConfig speechConfig, KeywordRecognitionModel kwsModel){
        boolean continuousListeningStarted = false;
        SpeechRecognizer reco = null;
        AudioConfig audioInput = null;
        String buttonText = "";
        ArrayList<String> content = new ArrayList<>();
        try {
            content.clear();

            // 检查当前状态
            if (isBluetoothFragmentActive || isVoiceFunctionsPaused) {
                Log.i(logTag, "当前状态不适合启动关键词识别");
                return;
            }

            audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
            reco = new SpeechRecognizer(speechConfig, audioInput);

            reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                final String s = speechRecognitionResultEventArgs.getResult().getText();
                Log.i(logTag, "Intermediate result received: " + s);
            });

            reco.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                final String s;
                final String word;
                if (speechRecognitionResultEventArgs.getResult().getReason() == ResultReason.RecognizedKeyword)
                {
                    word=speechRecognitionResultEventArgs.getResult().getText();
                    s = "Keyword: " + word;
                    Log.i(logTag, "Keyword recognized result received: " + s);

                    // 检查状态
                    if (isBluetoothFragmentActive || isVoiceFunctionsPaused) {
                        Log.i(logTag, "状态已改变，停止语音处理");
                        return;
                    }

                    String welcome="我在听请讲。";
                    speak(welcome,()->{
                        if (!isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                            Regnize(speechConfig);
                        }
                    });

                }
                else
                {
                    word=speechRecognitionResultEventArgs.getResult().getText();
                    s = "Recognized: " + word;
                    Log.i(logTag, "Final result received: " + s);
                }
            });

            final Future<Void> task = reco.startKeywordRecognitionAsync(kwsModel);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            Log.e(logTag, "关键词识别启动失败: " + ex.getMessage());
        }
    }

    /**
     * 显示运动控制命令的反馈提示
     * @param action 动作名称（前进、后退、左转、右转、停止）
     */
    private void showMotionCommandFeedback(final String action) {
        runOnUiThread(() -> {
            // 1. Toast 视觉提示 - 使用 emoji 图标
            String icon;
            switch (action) {
                case "前进":
                    icon = "⬆️";
                    break;
                case "后退":
                    icon = "⬇️";
                    break;
                case "左转":
                    icon = "⬅️";
                    break;
                case "右转":
                    icon = "➡️";
                    break;
                case "停止":
                    icon = "⏹️";
                    break;
                default:
                    icon = "✓";
            }
            String message = icon + " " + action;
            Toast toast = Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT);
            toast.show();

            // 2. 震动触觉反馈
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8.0+ 使用 VibrationEffect
                    android.os.VibrationEffect effect = android.os.VibrationEffect.createOneShot(
                            100, // 震动 100 毫秒
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    );
                    vibrator.vibrate(effect);
                } else {
                    // 旧版本 Android
                    vibrator.vibrate(100);
                }
            }

            Log.i(logTag, "运动命令反馈: " + action);
        });
    }

    /**
     * 检测语音文本中的控制关键词并执行蓝牙命令
     * @param text 识别到的语音文本
     */
    private void detectAndExecuteVoiceCommand(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // 检查蓝牙服务是否可用
        if (bluetoothService == null || !isBluetoothServiceBound) {
            Log.w(logTag, "蓝牙服务未绑定，无法执行语音控制命令");
            return;
        }

        // 检查蓝牙是否已连接
        if (!bluetoothService.isConnected()) {
            Log.w(logTag, "蓝牙未连接，无法执行语音控制命令");
            return;
        }

        String command = null;
        String action = null;

        // 检测控制关键词
        if (text.contains("前进")) {
            command = CMD_FORWARD;
            action = "前进";
        } else if (text.contains("后退")) {
            command = CMD_BACKWARD;
            action = "后退";
        } else if (text.contains("停止") || text.contains("停下")) {
            command = CMD_STOP;
            action = "停止";
        } else if (text.contains("左转") || text.contains("向左")) {
            command = CMD_LEFT;
            action = "左转";
        } else if (text.contains("右转") || text.contains("向右")) {
            command = CMD_RIGHT;
            action = "右转";
        }

        // 如果检测到命令，发送到下位机
        if (command != null) {
            try {
                bluetoothService.sendData(command);
                Log.i(logTag, "语音控制: 检测到【" + action + "】命令，已发送指令: " + command);

                // 显示反馈提示
                showMotionCommandFeedback(action);
            } catch (Exception e) {
                Log.e(logTag, "发送蓝牙控制命令失败: " + e.getMessage());
            }
        }
    }

    private void Regnize(SpeechConfig speechConfig){
        final String logTag = "reco 1";

        try {
            // 检查状态
            if (isBluetoothFragmentActive || isVoiceFunctionsPaused) {
                Log.i(logTag, "当前状态不适合启动语音识别");
                return;
            }

            final AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
            final SpeechRecognizer reco = new SpeechRecognizer(speechConfig, audioInput);
            task = reco.recognizeOnceAsync();
            setOnTaskCompletedListener(task, result -> {
                String s = "";
                if (result.getReason() == ResultReason.RecognizedSpeech) {
                    s = result.getText();
                }else if (result.getReason()==ResultReason.Canceled){
                    reco.close();
                    no_reg_count=0;
                    Log.i(logTag, "Reset");

                    // 检查状态
                    if (isBluetoothFragmentActive || isVoiceFunctionsPaused) {
                        Log.i(logTag, "状态已改变，停止语音处理");
                        return;
                    }

                    String quit="Let start from begin. Please wait a moment!";
                    speak(quit,()->{
                        if (!isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                            RegnizeKeyword( speechConfig, kwsModel);
                        }
                    });
                    return;
                }

                reco.close();
                Log.i(logTag, "Recognizer returned: " + s);
                Log.i(logTag, "语音识别文本: 【" + s + "】，准备检测控制命令");

                // 检查状态
                if (isBluetoothFragmentActive || isVoiceFunctionsPaused) {
                    Log.i(logTag, "状态已改变，停止语音处理");
                    return;
                }

                if(s==""){
                    no_reg_count+=1;
                    Log.i(logTag, "no_reg_count: " + no_reg_count+"  s:"+s);
                    if (no_reg_count>=3){
                        String quit="我先退下了，您可以再次唤醒我说：你好！";
                        speak(quit,()->{
                            if (!isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                                RegnizeKeyword( speechConfig, kwsModel);
                            }
                        });
                    }else{
                        String nohear="我没有听清，您还在说话吗？";
                        speak(nohear,()->{
                            if (!isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                                Regnize(speechConfig);
                            }
                        });
                    }
                }else{
                    no_reg_count=0;

                    // 检测并执行语音控制命令
                    detectAndExecuteVoiceCommand(s);

                    String res = ChatAPI.generateText(s);
                    speak(res,()->{
                        if (!isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                            Regnize(speechConfig);
                        }
                    });
                }
            });
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            Log.e(logTag, "语音识别启动失败: " + ex.getMessage());
        }
    }

    private void speak(String s,ICallback callback){
        // 检查状态
        if (isBluetoothFragmentActive || isVoiceFunctionsPaused) {
            Log.i(logTag, "当前状态不适合语音合成");
            return;
        }

        speakingRunnable = new SpeakingRunnable();
        speakingRunnable.setContent(s);
        if (callback!=null){
            speakingRunnable.setCallback(callback);
        }
        singleThreadExecutor.execute(speakingRunnable);
    }

    private <T> void setOnTaskCompletedListener(Future<T> task, OnTaskCompletedListener<T> listener) {
        s_executorService.submit(() -> {
            T result = task.get();
            try {
                listener.onCompleted(result);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    private interface OnTaskCompletedListener<T> {
        void onCompleted(T taskResult) throws IOException;
    }

    private String copyAssetToCacheAndGetFilePath(String filename) {
        File cacheFile = new File(getCacheDir() + "/" + filename);
        if (!cacheFile.exists()) {
            try {
                InputStream is = getAssets().open(filename);
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                FileOutputStream fos = new FileOutputStream(cacheFile);
                fos.write(buffer);
                fos.close();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return cacheFile.getPath();
    }

    private static ExecutorService s_executorService;
    static {
        s_executorService = Executors.newCachedThreadPool();
    }

    // 提供给Fragment使用的蓝牙服务获取方法
    public BluetoothService getBluetoothService() {
        return bluetoothService;
    }

    public boolean isBluetoothServiceBound() {
        return isBluetoothServiceBound;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.i(logTag, "MainActivity onDestroy开始清理资源");

        // 首先标记为停止状态
        isBluetoothFragmentActive = true;
        isVoiceFunctionsPaused = true;

        // 停止语音功能
        pauseVoiceFunctions();

        // 解绑蓝牙服务
        if (isBluetoothServiceBound) {
            try {
                unbindService(bluetoothServiceConnection);
                isBluetoothServiceBound = false;
                Log.i(logTag, "蓝牙服务已解绑");
            } catch (Exception e) {
                Log.e(logTag, "解绑蓝牙服务失败: " + e.getMessage());
            }
        }

        // 释放语音合成器资源
        if (synthesizer != null) {
            try {
                synthesizer.close();
                Log.i(logTag, "语音合成器已关闭");
            } catch (Exception e) {
                Log.e(logTag, "关闭语音合成器失败: " + e.getMessage());
            }
        }
        if (connection != null) {
            try {
                connection.close();
                Log.i(logTag, "连接已关闭");
            } catch (Exception e) {
                Log.e(logTag, "关闭连接失败: " + e.getMessage());
            }
        }

        // 释放音频轨道资源
        if (audioTrack != null) {
            try {
                if (singleThreadExecutor != null) {
                    singleThreadExecutor.shutdownNow();
                    Log.i(logTag, "线程池已关闭");
                }
                audioTrack.stop();
                audioTrack.release();
                Log.i(logTag, "音频轨道已释放");
            } catch (Exception e) {
                Log.e(logTag, "释放音频轨道失败: " + e.getMessage());
            }
        }

        // 释放麦克风资源
        releaseMicrophoneStream();

        Log.i(logTag, "MainActivity资源清理完成");
    }

    interface ICallback {
        void Callback();
    }

    class SpeakingRunnable implements Runnable {
        private String content;
        private ICallback _callback;
        private ICallback _stopCallback;
        private volatile boolean shouldStop = false;

        public void setContent(String content) {
            this.content = content;
        }

        public void setCallback(ICallback callback) {
            _callback = callback;
        }

        public void stop(ICallback stopCallback) {
            shouldStop = true;
            _stopCallback = stopCallback;
            // 安全停止音频轨道播放
            if (audioTrack != null) {
                try {
                    if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.pause();
                    }
                } catch (Exception e) {
                    Log.e(logTag, "停止音频轨道时出错: " + e.getMessage());
                }
            }
        }

        @Override
        public void run() {
            try {
                // 检查是否需要停止
                if (shouldStop || isBluetoothFragmentActive || isVoiceFunctionsPaused) {
                    if (_stopCallback != null) {
                        _stopCallback.Callback();
                    }
                    return;
                }

                // 安全启动音频轨道
                if (audioTrack != null && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        audioTrack.play();
                    } catch (Exception e) {
                        Log.e(logTag, "启动音频轨道失败: " + e.getMessage());
                        return;
                    }
                }

                synchronized (synchronizedObj) {
                    stopped = false;
                }

                String ssmlContent = "<speak xmlns=\"http://www.w3.org/2001/10/synthesis\" xmlns:mstts=\"http://www.w3.org/2001/mstts\" xmlns:emo=\"http://www.w3.org/2009/10/emotionml\" version=\"1.0\"  xml:lang=\"zh-CN\"><voice name=\"zh-CN-XiaoxiaoMultilingualNeural\"><lang xml:lang=\"zh-CN\"><prosody rate=\"17%\">"+content.replace("*"," ")+"</prosody></lang></voice></speak> ";

                SpeechSynthesisResult result = synthesizer.StartSpeakingSsmlAsync(ssmlContent).get();
                AudioDataStream audioDataStream = AudioDataStream.fromResult(result);

                byte[] buffer = new byte[2400];
                while (!shouldStop && !stopped && !isBluetoothFragmentActive && !isVoiceFunctionsPaused) {
                    long len = audioDataStream.readData(buffer);
                    if (len == 0) {
                        break;
                    }
                    if (!shouldStop && audioTrack != null) {
                        try {
                            audioTrack.write(buffer, 0, (int) len);
                        } catch (Exception e) {
                            Log.e(logTag, "写入音频数据失败: " + e.getMessage());
                            break;
                        }
                    }
                }

                audioDataStream.close();

                if (shouldStop && _stopCallback != null) {
                    _stopCallback.Callback();
                    return;
                }

                // 再次检查状态
                if (!shouldStop && !isBluetoothFragmentActive && !isVoiceFunctionsPaused && _callback != null) {
                    _callback.Callback();
                }
            } catch (Exception ex) {
                Log.e("Speech Synthesis Demo", "unexpected " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
}
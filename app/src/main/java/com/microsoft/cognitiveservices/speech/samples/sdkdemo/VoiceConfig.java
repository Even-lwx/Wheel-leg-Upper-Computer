package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

/**
 * 语音服务配置常量类
 * 集中管理所有 API 密钥和配置参数
 */
public class VoiceConfig {

    // ==================== Azure 语音服务配置 ====================
    /**
     * Azure Speech Service 订阅密钥
     * 注意: 请在本地配置真实密钥，不要提交到 Git
     * 获取方式: https://portal.azure.com -> Cognitive Services -> Speech Services
     */
    public static final String AZURE_SPEECH_KEY = "YOUR_AZURE_SPEECH_KEY_HERE";

    /**
     * Azure Speech Service 区域
     */
    public static final String AZURE_SPEECH_REGION = "eastus";

    /**
     * Azure TTS 语音名称 (多语言神经语音)
     */
    public static final String AZURE_VOICE_NAME = "zh-CN-XiaoxiaoMultilingualNeural";

    /**
     * 语音识别语言
     */
    public static final String SPEECH_RECOGNITION_LANGUAGE = "zh-CN";

    /**
     * 语音合成语言
     */
    public static final String SPEECH_SYNTHESIS_LANGUAGE = "zh-CN";

    // ==================== DeepSeek API 配置 ====================
    /**
     * DeepSeek API 密钥
     * 注意: 请在本地配置真实密钥，不要提交到 Git
     * 获取方式: https://platform.deepseek.com
     */
    public static final String DEEPSEEK_API_KEY = "YOUR_DEEPSEEK_API_KEY_HERE";

    /**
     * DeepSeek API 端点
     */
    public static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";

    /**
     * DeepSeek 模型名称
     */
    public static final String DEEPSEEK_MODEL = "deepseek-chat";

    /**
     * DeepSeek 最大 tokens (语音场景适合较短回复)
     */
    public static final int DEEPSEEK_MAX_TOKENS = 500;

    // ==================== 语音合成参数配置 ====================
    /**
     * TTS 语速调整 (SSML prosody rate)
     * 推荐值: 0-50% (正值加快,负值放慢)
     */
    public static final String TTS_SPEECH_RATE = "17%";

    /**
     * TTS 音量调整 (SSML prosody volume)
     * 推荐值: 0-100% (提升播报清晰度)
     */
    public static final String TTS_VOLUME = "+50%";

    /**
     * AudioTrack 采样率 (Hz)
     */
    public static final int AUDIO_SAMPLE_RATE = 24000;

    /**
     * AudioTrack 缓冲区大小倍数
     */
    public static final int AUDIO_BUFFER_MULTIPLIER = 2;

    // ==================== 蓝牙通信配置 ====================
    /**
     * 蓝牙 SPP 协议 UUID
     */
    public static final String BLUETOOTH_SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    /**
     * 蓝牙遥控命令定义
     */
    public static final String COMMAND_FORWARD = "1";  // 前进
    public static final String COMMAND_BACKWARD = "2"; // 后退
    public static final String COMMAND_STOP = "3";     // 停止
    public static final String COMMAND_LEFT = "4";     // 左转
    public static final String COMMAND_RIGHT = "5";    // 右转

    // ==================== 应用行为配置 ====================
    /**
     * 连续语音识别未听清最大次数
     */
    public static final int MAX_NO_MATCH_COUNT = 3;

    /**
     * Fragment 切换延迟 (ms) - 防止资源冲突
     */
    public static final int FRAGMENT_SWITCH_DELAY_MS = 500;

    /**
     * 语音功能恢复延迟 (ms)
     */
    public static final int VOICE_RESUME_DELAY_MS = 1500;

    /**
     * 音频焦点释放延迟 (ms)
     */
    public static final int AUDIO_FOCUS_RELEASE_DELAY_MS = 500;

    // ==================== 日志标签 ====================
    public static final String LOG_TAG_MAIN = "MainActivity";
    public static final String LOG_TAG_BLUETOOTH_SERVICE = "BluetoothService";
    public static final String LOG_TAG_BLUETOOTH_FRAGMENT = "BluetoothRemoteFragment";
    public static final String LOG_TAG_CHAT_API = "ChatAPI";
    public static final String LOG_TAG_PLACEHOLDER = "PlaceholderFragment";

    // ==================== 权限请求码 ====================
    public static final int PERMISSION_REQUEST_CODE = 1001;
    public static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 100;

    /**
     * 私有构造函数 - 工具类不应被实例化
     */
    private VoiceConfig() {
        throw new AssertionError("VoiceConfig 是常量工具类,不应被实例化");
    }
}

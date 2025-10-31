package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

/**
 * 本地 API Keys 配置文件模板
 * 
 * 📝 使用步骤：
 * 1. 复制此文件，重命名为 LocalApiKeys.java
 * 2. 填入你的真实 API Keys
 * 3. LocalApiKeys.java 已添加到 .gitignore，不会被提交
 * 
 * ⚠️ 不要修改此模板文件的文件名！
 */
public class LocalApiKeys_Template {

    // ==================== Azure 语音服务配置 ====================
    /**
     * Azure Speech Service 订阅密钥
     * 获取方式: https://portal.azure.com -> Cognitive Services -> Speech Services
     */
    public static final String AZURE_SPEECH_KEY = "YOUR_AZURE_SPEECH_KEY_HERE";

    /**
     * Azure Speech Service 区域
     * 常用区域: eastus, westus, westeurope, southeastasia 等
     */
    public static final String AZURE_SPEECH_REGION = "eastus";

    // ==================== DeepSeek API 配置 ====================
    /**
     * DeepSeek API 密钥
     * 获取方式: https://platform.deepseek.com
     */
    public static final String DEEPSEEK_API_KEY = "YOUR_DEEPSEEK_API_KEY_HERE";
}

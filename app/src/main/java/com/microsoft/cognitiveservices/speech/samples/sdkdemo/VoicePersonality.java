package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 语音助手人格配置管理类
 * 提供5种预设人格和自定义人格支持
 */
public class VoicePersonality {

    private static final String PREFS_NAME = "VoicePersonalityPrefs";
    private static final String KEY_SELECTED_PERSONALITY = "selected_personality";
    private static final String KEY_CUSTOM_PROMPT = "custom_prompt";

    // 预设人格 ID
    public static final int PERSONALITY_PROFESSIONAL = 0; // 专业助手
    public static final int PERSONALITY_LIVELY = 1;       // 活泼助手
    public static final int PERSONALITY_GENTLE = 2;       // 温柔助手
    public static final int PERSONALITY_CONCISE = 3;      // 简洁助手
    public static final int PERSONALITY_CUSTOM = 4;       // 自定义

    /**
     * 人格数据类
     */
    public static class Personality {
        public int id;
        public String name;
        public String description;
        public String systemPrompt;
        public double temperature;

        public Personality(int id, String name, String description, String systemPrompt, double temperature) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.systemPrompt = systemPrompt;
            this.temperature = temperature;
        }
    }

    // 预设人格列表
    private static final Personality[] PRESET_PERSONALITIES = {
        // 专业助手
        new Personality(
            PERSONALITY_PROFESSIONAL,
            "专业助手",
            "严谨准确,适合技术问题",
            "你是一个专业的AI语音助手,用简洁准确的语言回答问题。" +
            "回复要口语化自然,不要使用任何Markdown格式化符号(如##、**、*、-、`等)。" +
            "回答要精准可靠,适合技术性问题。",
            0.3
        ),

        // 活泼助手
        new Personality(
            PERSONALITY_LIVELY,
            "活泼助手",
            "幽默风趣,日常聊天",
            "你是一个活泼有趣的AI助手,用轻松幽默的方式与用户对话。" +
            "保持口语化表达,让对话充满趣味,但不要使用任何格式化文本符号。" +
            "回答要简洁,避免冗长。",
            0.5
        ),

        // 温柔助手
        new Personality(
            PERSONALITY_GENTLE,
            "温柔助手",
            "亲切温暖,情感陪伴",
            "你是一个温柔体贴的AI伙伴,用亲切温暖的语气回应用户。" +
            "理解用户的情感需求,给予温暖的陪伴和建议。" +
            "避免使用任何Markdown符号,保持自然对话的温度感。",
            0.4
        ),

        // 简洁助手
        new Personality(
            PERSONALITY_CONCISE,
            "简洁助手",
            "言简意赅,快速答疑",
            "你是一个高效的AI助手,用最简洁的语言直接回答问题。" +
            "一句话概括核心答案,不展开不啰嗦,不使用任何格式化符号。" +
            "追求极致简洁,快速解决用户疑问。",
            0.3
        ),

        // 自定义
        new Personality(
            PERSONALITY_CUSTOM,
            "自定义",
            "完全自定义AI行为",
            "", // 自定义提示词由用户输入
            0.4
        )
    };

    /**
     * 获取所有预设人格
     */
    public static Personality[] getPresetPersonalities() {
        return PRESET_PERSONALITIES;
    }

    /**
     * 根据 ID 获取人格
     */
    public static Personality getPersonalityById(int id) {
        if (id >= 0 && id < PRESET_PERSONALITIES.length) {
            return PRESET_PERSONALITIES[id];
        }
        return PRESET_PERSONALITIES[PERSONALITY_PROFESSIONAL]; // 默认返回专业助手
    }

    /**
     * 保存当前选择的人格
     */
    public static void saveSelectedPersonality(Context context, int personalityId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SELECTED_PERSONALITY, personalityId).apply();
    }

    /**
     * 保存自定义提示词
     */
    public static void saveCustomPrompt(Context context, String customPrompt) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CUSTOM_PROMPT, customPrompt).apply();
    }

    /**
     * 获取当前选择的人格 ID
     */
    public static int getSelectedPersonalityId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SELECTED_PERSONALITY, PERSONALITY_PROFESSIONAL);
    }

    /**
     * 获取自定义提示词
     */
    public static String getCustomPrompt(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_CUSTOM_PROMPT, "你是一个友好的AI语音助手,用自然口语化的方式回答问题,不要使用任何格式化符号。");
    }

    /**
     * 获取当前人格的 System Prompt
     */
    public static String getCurrentSystemPrompt(Context context) {
        int personalityId = getSelectedPersonalityId(context);
        Personality personality = getPersonalityById(personalityId);

        if (personalityId == PERSONALITY_CUSTOM) {
            return getCustomPrompt(context);
        } else {
            return personality.systemPrompt;
        }
    }

    /**
     * 获取当前人格的 Temperature
     */
    public static double getCurrentTemperature(Context context) {
        int personalityId = getSelectedPersonalityId(context);
        Personality personality = getPersonalityById(personalityId);
        return personality.temperature;
    }

    /**
     * 获取当前人格的名称
     */
    public static String getCurrentPersonalityName(Context context) {
        int personalityId = getSelectedPersonalityId(context);
        Personality personality = getPersonalityById(personalityId);
        return personality.name;
    }
}

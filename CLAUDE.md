# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Android 的轮腿车上位机控制应用，集成了语音识别、语音合成和蓝牙遥控功能。应用使用 Microsoft Azure 语音服务和 DeepSeek AI 实现智能语音交互，并通过蓝牙 SPP 协议控制下位机设备。

**项目名称**: Speech Recognition (内部为 Wheel-leg Upper Computer)
**包名**: `com.microsoft.cognitiveservices.speech.samples.sdkdemo`
**最低 SDK**: 26 (Android 8.0)
**目标 SDK**: 31 (Android 12)
**构建工具**: Gradle 8.6.0

## 构建与运行命令

```bash
# 清理构建产物
gradlew clean

# 构建 debug APK
gradlew assembleDebug

# 构建 release APK
gradlew assembleRelease

# 安装并运行到设备
gradlew installDebug

# 查看日志（关键标签）
adb logcat -s MainActivity BluetoothService BluetoothRemoteFragment ChatAPI
```

## 核心架构

### 1. 双 Fragment 架构

应用采用单 Activity + 双 Fragment 的架构设计：

- **MainActivity**: 主活动，负责 Fragment 切换和蓝牙服务绑定
  - 管理语音功能和蓝牙功能的生命周期冲突
  - 处理音频焦点切换以避免干扰
  - 绑定并管理 `BluetoothService`

- **PlaceholderFragment**: 语音助手界面
  - 关键词唤醒（"你好"）
  - 连续语音识别
  - AI 对话（DeepSeek-v3）
  - 语音合成播报

- **BluetoothRemoteFragment**: 蓝牙遥控界面
  - 蓝牙设备搜索和配对
  - 遥控命令发送（前进/后退/左转/右转/停止）
  - 连接状态管理

### 2. 蓝牙通信架构

**BluetoothService** (后台服务，持久运行)：
- 使用 SPP 协议（UUID: `00001101-0000-1000-8000-00805F9B34FB`）
- **三层连接策略**（自动降级尝试）：
  1. 标准 `createRfcommSocketToServiceRecord(UUID)`
  2. 反射方法 `createRfcommSocket(1)`
  3. 不安全连接 `createInsecureRfcommSocketToServiceRecord(UUID)`
- 异步数据收发，单独的读取线程
- 自动重连机制通过 `BroadcastReceiver` 监听连接状态
- 服务绑定器模式：使用 `LocalBinder` 与 Activity/Fragment 通信
- 连接状态回调接口：`BluetoothConnectionListener`

**关键方法**：
- `connectToDevice(BluetoothDevice)`: 建立连接
- `sendData(String)`: 发送命令
- `startReading()`: 启动异步数据读取线程
- `disconnect()`: 断开连接并清理资源

**遥控命令定义** (单字符协议)：
```java
前进: "1"
后退: "2"
停止: "3"
左转: "4"
右转: "5"
```

### 3. 语音识别与合成流程

**初始化流程**：
1. `MainActivity.checkAndRequestPermissions()` - 检查和请求必要权限
2. `initializeVoiceSDK()` - 初始化 Azure Speech SDK
3. 配置 `AudioTrack` (24kHz, 16bit, Mono)
4. 建立 WebSocket 连接到 Azure 服务
5. 加载关键词模型 (`nihao.table` 文件)
6. 启动关键词识别器 `KeywordRecognizer`

**语音交互循环**：
```
关键词识别 → 唤醒提示音 → 连续识别 → DeepSeek AI 对话 → 语音合成播报 → 返回识别
                                  ↓ (3次未听清或超时)
                              退出到关键词识别
```

**ChatAPI 消息管理**：
- 保留最近 10 条消息避免 token 溢出
- 自动剔除开头的 `tool` 角色消息保证对话连贯性
- 使用 Jackson ObjectMapper 处理 JSON 序列化

**状态管理关键点**：
- `isBluetoothFragmentActive`: 控制是否暂停语音功能
- `isVoiceFunctionsPaused`: 标记语音功能暂停状态
- `hasAudioFocus`: 音频焦点持有状态

### 4. Fragment 切换保护机制

为防止语音功能干扰蓝牙连接，实现了完整的状态隔离：

```java
// 切换到蓝牙 Fragment 时 (MainActivity)
pauseVoiceFunctions() {
    - 停止语音播放和识别
    - 释放麦克风资源
    - 暂停 AudioTrack
    - 延迟 500ms 放弃音频焦点（避免冲突）
    - 设置 isVoiceFunctionsPaused = true
}

// 切换回语音 Fragment 时 (MainActivity)
resumeVoiceFunctions() {
    - 检查蓝牙音频状态（避免冲突）
    - 延迟 1500ms 初始化语音功能
    - 重新请求音频焦点
    - 恢复关键词识别
    - 设置 isVoiceFunctionsPaused = false
}
```

**延迟设计原因**：
- 音频资源释放需要时间
- 防止 Fragment 快速切换导致资源竞争
- 确保蓝牙音频优先级高于语音功能

## 配置说明

### ⚠️ 必须配置的 API 密钥

**重要**: 代码中当前包含占位符 API 密钥，必须替换为真实密钥后才能正常运行。

**MainActivity.java** (约第 94-96 行)：
```java
private static final String SpeechSubscriptionKey = "xxxx"; // Azure Speech Key
private static final String SpeechRegion = "eastus";        // Azure Region
```

**ChatAPI.java** (约第 57 行)：
```java
connection.setRequestProperty("Authorization", "Bearer xxxx"); // DeepSeek API Key
```

DeepSeek API 配置：
- 端点: `https://api.deepseek.com/chat/completions`
- 模型: `deepseek-chat`
- 温度: 0.6
- 最大 Tokens: 500

### 关键词模型文件

关键词唤醒模型位于 `app/src/main/assets/nihao.table`，当前配置为"你好"。

修改唤醒词需要：
1. 使用 Azure Speech Studio 生成新的 `.table` 文件
2. 替换 assets 中的文件
3. 更新 MainActivity.java 中的 `KwsModelFile` 常量

### UI 资源结构

应用使用圆角设计和主题色系统：
- 主题色: `#BD5742` (棕红色)
- 圆角背景 drawable：`bg_rounded_*.xml` 系列
- 布局文件：
  - `activity_main.xml`: 主容器 + BottomNavigationView
  - `fragment_placeholder.xml`: 语音助手界面 (简洁设计)
  - `fragment_bluetooth_remote.xml`: 蓝牙遥控界面 (复杂控制面板)
- 底部导航菜单: `menu/bottom_nav_menu.xml`

## 关键依赖

```gradle
// Azure 语音服务 SDK
implementation 'com.microsoft.cognitiveservices.speech:client-sdk:1.34.0'

// JSON 处理（DeepSeek API）
implementation 'com.fasterxml.jackson.core:jackson-databind:2.13.0'

// HTTP 客户端
implementation 'org.apache.httpcomponents:httpclient-android:4.3.5.1'

// Diff 工具（发音评估）
implementation 'io.github.java-diff-utils:java-diff-utils:4.11'

// Glide (GIF 动画支持)
implementation 'com.github.bumptech.glide:glide:4.12.0'

// Material Design 组件
implementation 'com.google.android.material:material:1.5.0'
```

**编译配置**:
- `compileSdk`: 36
- `minSdkVersion`: 26 (Android 8.0)
- `targetSdkVersion`: 31 (Android 12)
- Java 版本: 1.8
- 必须启用参数名编译: `options.compilerArgs += ['-parameters']`

## 权限需求

应用需要以下运行时权限（MainActivity 自动请求）：

- `RECORD_AUDIO`: 语音识别
- `INTERNET`: AI 对话和 TTS
- `BLUETOOTH_CONNECT/SCAN`: 蓝牙通信 (Android 12+)
- `BLUETOOTH/BLUETOOTH_ADMIN`: 蓝牙通信 (Android 11-)
- `ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION`: 蓝牙设备发现
- `VIBRATE`: 震动反馈
- `READ_EXTERNAL_STORAGE`: 读取外部存储

**AndroidManifest.xml** 中已声明所有必要权限，但部分权限需要运行时动态授予。

## 常见问题定位

### 语音识别无响应
- 检查日志中 `MainActivity` 标签的麦克风流创建
- 确认 `kwsModel` 加载成功
- 验证 Azure 密钥和区域配置

### 蓝牙连接失败
- 查看 `BluetoothService` 日志中的三种连接尝试
- 确认目标设备支持 SPP 协议
- 检查 Android 12+ 设备的蓝牙权限授予

### Fragment 切换后音频异常
- 确认 `pauseVoiceFunctions()` 和 `resumeVoiceFunctions()` 调用
- 检查音频焦点请求/释放日志
- 验证 `isBluetoothFragmentActive` 状态同步

### DeepSeek API 调用失败
- 确认 `ChatAPI.java:57` 中的 API Key 有效性
- 检查网络连接和 HTTPS 证书
- 查看 API 响应错误码（logcat 中 `ChatAPI` 标签）

## 代码风格说明

- **注释语言**: 中文（项目主语言）
- **日志标签**: 使用类名作为 TAG，便于 logcat 过滤
- **日志输出**: 使用中文描述，便于本地化调试
- **UI 文本**: 使用 emoji 增强用户体验（蓝牙界面）
- **异步操作**: 统一使用 `Thread` 或 `ExecutorService`
- **主线程保护**: 避免在主线程进行网络和蓝牙操作
- **资源命名**: 使用前缀区分资源类型（`bg_`、`btn_`、`fragment_` 等）

## 核心类职责划分

| 类名 | 职责 | 关键特性 |
|------|------|----------|
| `MainActivity` | 生命周期管理、Fragment 切换、服务绑定 | 音频焦点管理、权限请求 |
| `PlaceholderFragment` | 语音助手 UI | GIF 动画、简洁界面 |
| `BluetoothRemoteFragment` | 蓝牙遥控 UI | 设备列表、遥控按钮 |
| `BluetoothService` | 蓝牙后台服务 | 持久连接、异步 IO |
| `ChatAPI` | DeepSeek AI 接口 | HTTP 调用、JSON 解析、消息历史管理 |
| `MicrophoneStream` | 麦克风输入流 | 音频数据采集 |
| `CustomTools` | 工具类 | 通用辅助方法 |

## 测试建议

由于涉及硬件（麦克风、蓝牙）和外部服务（Azure、DeepSeek），建议：

1. **单元测试**: 测试 JSON 解析和命令生成逻辑
2. **集成测试**: 使用 Mock 服务测试语音和 AI 流程
3. **真机测试**: 蓝牙和音频功能必须在真机上验证
4. **压力测试**: 长时间语音交互和频繁 Fragment 切换

## 性能优化要点

- 语音合成使用流式播放（AudioTrack + AudioDataStream）
- 蓝牙数据使用单独线程异步读取
- Fragment 切换时延迟初始化（300-1500ms）避免资源冲突
- DeepSeek 对话保留最近 10 条消息防止 token 溢出

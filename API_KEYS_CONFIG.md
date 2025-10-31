# API Keys 配置说明

⚠️ **重要**: 本项目需要配置 API Keys 才能正常运行。

## 🚀 快速配置（推荐方式）

### 方式一：使用本地配置文件（推荐）

1. **找到模板文件**:
   ```
   app/src/main/java/com/microsoft/cognitiveservices/speech/samples/sdkdemo/LocalApiKeys_Template.java
   ```

2. **复制并重命名**:
   - 复制 `LocalApiKeys_Template.java`
   - 重命名为 `LocalApiKeys.java`（去掉 `_Template` 后缀）

3. **填入真实密钥**:
   打开 `LocalApiKeys.java`，替换占位符：
   ```java
   public static final String AZURE_SPEECH_KEY = "你的Azure密钥";
   public static final String AZURE_SPEECH_REGION = "eastus"; // 你的区域
   public static final String DEEPSEEK_API_KEY = "你的DeepSeek密钥";
   ```

4. **完成** 🎉
   - `LocalApiKeys.java` 已添加到 `.gitignore`，不会被提交到 Git
   - 可以安全地保存真实密钥在本地

---

## 📋 需要配置的 API Keys

### 1. Azure Speech Service API Key

**文件位置**: `app/src/main/java/com/microsoft/cognitiveservices/speech/samples/sdkdemo/VoiceConfig.java`

**配置项**:
```java
public static final String AZURE_SPEECH_KEY = "YOUR_AZURE_SPEECH_KEY_HERE";
public static final String AZURE_SPEECH_REGION = "eastus"; // 根据实际区域修改
```

**获取方式**:
1. 访问 [Azure Portal](https://portal.azure.com)
2. 创建或选择 **Cognitive Services** → **Speech Services**
3. 在 "Keys and Endpoint" 页面复制 **Key 1** 或 **Key 2**
4. 记录你的 **Region**（如 eastus、westus 等）

**参考文档**: https://learn.microsoft.com/azure/cognitive-services/speech-service/

---

### 2. DeepSeek API Key

**文件位置**: `app/src/main/java/com/microsoft/cognitiveservices/speech/samples/sdkdemo/VoiceConfig.java`

**配置项**:
```java
public static final String DEEPSEEK_API_KEY = "YOUR_DEEPSEEK_API_KEY_HERE";
```

**获取方式**:
1. 访问 [DeepSeek Platform](https://platform.deepseek.com)
2. 注册账号并登录
3. 在 **API Keys** 页面创建新的 API Key
4. 复制生成的密钥（格式：sk-xxxxxxxxxxxxxxxx）

**参考文档**: https://platform.deepseek.com/docs

---

## 🔧 配置步骤

1. **打开配置文件**:
   ```
   app/src/main/java/com/microsoft/cognitiveservices/speech/samples/sdkdemo/VoiceConfig.java
   ```

2. **替换占位符**:
   - 找到 `YOUR_AZURE_SPEECH_KEY_HERE`，替换为你的 Azure Speech Key
   - 找到 `YOUR_DEEPSEEK_API_KEY_HERE`，替换为你的 DeepSeek API Key
   - 根据需要修改 `AZURE_SPEECH_REGION`

3. **保存文件** (但不要提交到 Git)

4. **编译运行**:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🔒 安全建议

### ⚠️ 不要将真实 API Keys 提交到 Git！

**推荐做法**:

1. **使用 .gitignore** (已配置):
   ```gitignore
   # 本地配置文件
   local.properties
   VoiceConfig.java
   ```

2. **使用环境变量** (高级):
   ```java
   public static final String AZURE_SPEECH_KEY = 
       System.getenv("AZURE_SPEECH_KEY") != null ? 
       System.getenv("AZURE_SPEECH_KEY") : "YOUR_AZURE_SPEECH_KEY_HERE";
   ```

3. **使用 BuildConfig** (推荐):
   在 `build.gradle` 中配置:
   ```gradle
   android {
       defaultConfig {
           buildConfigField "String", "AZURE_SPEECH_KEY", "\"${project.findProperty('AZURE_SPEECH_KEY') ?: 'YOUR_KEY'}\""
       }
   }
   ```

4. **定期轮换密钥**: 
   - 如果 API Key 意外泄露，立即在控制台重新生成新密钥
   - Azure: Portal → 服务 → Regenerate keys
   - DeepSeek: Platform → Revoke & Create new

---

## 📞 支持

如有问题，请参考：
- Azure Speech Service: https://learn.microsoft.com/azure/cognitive-services/speech-service/
- DeepSeek API: https://platform.deepseek.com/docs
- 项目 Issues: https://github.com/Even-lwx/Wheel-leg-Upper-Computer/issues

---

**最后更新**: 2025年10月31日

# AI Chat（Android）

一个零第三方依赖的原生 Android 聊天 App，对接任意 **OpenAI 兼容**接口（`POST {BaseURL}/chat/completions`）。

## 特性

- 流式输出（SSE，逐字显示），可在设置里关掉改用整段返回
- Base URL / API Key / 模型名称全部在 App 内填写，不入库、不外传
- 多轮上下文、气泡对话、长按气泡复制、一键清空会话
- 无网络框架、无 AndroidX、无 Gradle 依赖，纯 `aapt + javac + d8 + apksigner` 构建，APK 约 100KB 量级

## 安装

到仓库 Actions 的 `Build AI Chat APK` 工作流里下载 `ai-chat-apk` artifact，安装 `AI-Chat.apk`
（Android 8.0+，首次安装需允许「安装未知来源应用」）。

## 首次使用

点右上角「设置」填写：

| 字段 | 示例 | 说明 |
| --- | --- | --- |
| 接口地址 | `https://api.deepseek.com/v1` | 不带 `/chat/completions` 也行，App 会自动补 |
| API Key | `sk-...` | 只存本机 SharedPreferences，不上传 |
| 模型名称 | `deepseek-chat` | 按服务商文档填，如 `deepseek-reasoner`、`gpt-4o-mini` 等 |
| 流式输出 | 开 | 关掉则等待完整回复后一次性显示 |

常见兼容地址：DeepSeek `https://api.deepseek.com/v1`、OpenAI `https://api.openai.com/v1`、
硅基流动 `https://api.siliconflow.cn/v1`、阿里云百炼 `https://dashscope.aliyuncs.com/compatible-mode/v1`。

## 本地构建

```bash
export ANDROID_JAR=$ANDROID_HOME/platforms/android-34/android.jar
export KEYSTORE=/path/to/release.jks KS_PASS=xxx KS_ALIAS=aichat
export PATH=$ANDROID_HOME/build-tools/34.0.0:$PATH
bash scripts/build.sh    # 产出 AI-Chat.apk
```

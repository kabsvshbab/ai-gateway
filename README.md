# AI Gateway

Multi-AI web reverse proxy gateway for Android. Exposes an OpenAI-compatible API (`/v1/chat/completions`) backed by hidden WebViews that piggyback on your logged-in web sessions.

## Supported Providers

| Provider | Models | Login URL |
|----------|--------|-----------|
| DeepSeek | deepseek-chat, deepseek-reasoner | chat.deepseek.com |
| ChatGPT | gpt-4o, gpt-4o-mini | chatgpt.com |
| Claude | claude-sonnet-4, claude-opus-4 | claude.ai |
| Gemini | gemini-2.0-flash, gemini-2.5-pro | gemini.google.com |
| Doubao | doubao, doubao-image | www.doubao.com |
| Kimi | kimi | kimi.moonshot.cn |
| Grok | grok-3, grok-3-thinking | grok.com |
| Qwen | qwen-max, qwen-plus | chat.qwen.ai |
| ERNIE | ernie-4.0, ernie-3.5 | yiyan.baidu.com |
| Yuanbao | hunyuan, deepseek-r1 | yuanbao.tencent.com |

## Architecture

```
Client (Cherry Studio, etc.)
    |  HTTP (OpenAI format)
    v
Local Gateway (127.0.0.1:8787)
    |  Route by model ID
    v
Hidden WebView Pool (one per provider)
    |  JS fetch() with credentials:'include'
    v
Upstream AI Web API
```

## Usage

1. Install APK
2. Grant overlay permission (for hidden WebViews)
3. Log in to each provider via the in-app browser
4. Point your OpenAI-compatible client to `http://127.0.0.1:8787/v1`

## Build

```bash
./gradlew assembleDebug
```

GitHub Actions builds APK on every push to main.

## Disclaimer

For personal use only. Respect each AI service's Terms of Service.
# Trigger rebuild Sun Oct 18 04:30:54 UTC 2026

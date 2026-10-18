package dev.aigateway.provider

import dev.aigateway.core.ProviderDef

/**
 * Registry of all supported web AI providers.
 * Hook scripts follow the doubao-gateway pattern: the page context performs
 * fetch() with credentials:'include', streaming chunks back through the
 * JavascriptInterface bridge `aigatewayHost.onChunk(requestId, data)`.
 */
object Providers {

    /** Generic hook: posts [payload] to the provider apiUrl, streams SSE data lines. */
    private fun genericHook(extractDelta: String) = """
(function () {
  if (window.__aigwHooked) return;
  window.__aigwHooked = true;
  window.__aigwAbort = {};
  window.__aigwSend = async function (url, payloadJson, requestId) {
    var bridge = window.aigatewayHost;
    function send(d) { try { bridge.onChunk(requestId, d); } catch (e) {} }
    try {
      var res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: payloadJson,
        credentials: 'include'
      });
      if (!res.ok) {
        var t = ''; try { t = (await res.text()).slice(0, 300); } catch (e) {}
        send('__HTTP_ERROR__:' + res.status + ':' + t);
        return;
      }
      var reader = res.body.getReader();
      var dec = new TextDecoder();
      var buf = '';
      while (true) {
        var chunk = await reader.read();
        if (chunk.done) break;
        buf += dec.decode(chunk.value, { stream: true });
        var idx;
        while ((idx = buf.indexOf('\n')) >= 0) {
          var line = buf.slice(0, idx); buf = buf.slice(idx + 1);
          if (line.charAt(line.length - 1) === '\r') line = line.slice(0, -1);
          if (line.indexOf('data:') !== 0) continue;
          var data = line.slice(5).trim();
          if (!data || data === '[DONE]' || data === '{}') continue;
          try {
            var obj = JSON.parse(data);
            var delta = (""" + extractDelta + "")(obj);
            if (delta) send(JSON.stringify({ delta: delta }));
          } catch (e) {}
        }
      }
      send('__DONE__');
    } catch (e) {
      if (e && e.name === 'AbortError') send('__DONE__');
      else send('__ERROR__:' + ((e && e.message) ? e.message : e));
    }
    delete window.__aigwAbort[requestId];
  };
})();
""".trimIndent()

    val ALL: List<ProviderDef> = listOf(
        ProviderDef(
            id = "deepseek",
            label = "DeepSeek",
            loginUrl = "https://chat.deepseek.com/",
            apiUrl = "https://chat.deepseek.com/api/v0/chat/completions",
            cookieDomain = ".deepseek.com",
            sessionCookieNames = listOf("ds_session_id", "userToken"),
            modelIds = listOf("deepseek-chat", "deepseek-reasoner"),
            hookScript = genericHook("function(o){return o.choices&&o.choices[0]&&o.choices[0].delta&&o.choices[0].delta.content||''}")
        ),
        ProviderDef(
            id = "chatgpt",
            label = "ChatGPT",
            loginUrl = "https://chatgpt.com/",
            apiUrl = "https://chatgpt.com/backend-api/conversation",
            cookieDomain = ".chatgpt.com",
            sessionCookieNames = listOf("__Secure-next-auth.session-token"),
            modelIds = listOf("gpt-4o", "gpt-4o-mini"),
            hookScript = genericHook("function(o){return o.message&&o.message.content&&o.message.content.parts&&o.message.content.parts[0]||''}")
        ),
        ProviderDef(
            id = "claude",
            label = "Claude",
            loginUrl = "https://claude.ai/new",
            apiUrl = "https://claude.ai/api/append_message",
            cookieDomain = ".claude.ai",
            sessionCookieNames = listOf("sessionKey"),
            modelIds = listOf("claude-sonnet-4", "claude-opus-4"),
            hookScript = genericHook("function(o){return o.delta&&o.delta.text||o.completion||''}")
        ),
        ProviderDef(
            id = "gemini",
            label = "Gemini",
            loginUrl = "https://gemini.google.com/app",
            apiUrl = "https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate",
            cookieDomain = ".google.com",
            sessionCookieNames = listOf("SID", "__Secure-1PSID"),
            modelIds = listOf("gemini-2.0-flash", "gemini-2.5-pro"),
            hookScript = genericHook("function(o){return ''}")
        ),
        ProviderDef(
            id = "doubao",
            label = "Doubao",
            loginUrl = "https://www.doubao.com/chat/",
            apiUrl = "https://www.doubao.com/samantha/chat/completion",
            cookieDomain = ".doubao.com",
            sessionCookieNames = listOf("sessionid"),
            modelIds = listOf("doubao", "doubao-image"),
            hookScript = genericHook("function(o){return o.choices&&o.choices[0]&&o.choices[0].delta&&o.choices[0].delta.content||''}")
        ),
        ProviderDef(
            id = "kimi",
            label = "Kimi",
            loginUrl = "https://kimi.moonshot.cn/",
            apiUrl = "https://kimi.moonshot.cn/api/chat",
            cookieDomain = ".moonshot.cn",
            sessionCookieNames = listOf("access_token", "refresh_token"),
            modelIds = listOf("kimi"),
            hookScript = genericHook("function(o){return o.choices&&o.choices[0]&&o.choices[0].delta&&o.choices[0].delta.content||''}")
        ),
        ProviderDef(
            id = "grok",
            label = "Grok",
            loginUrl = "https://grok.com/",
            apiUrl = "https://grok.com/rest/app-chat/conversations/new",
            cookieDomain = ".grok.com",
            sessionCookieNames = listOf("sso", "sso-rw"),
            modelIds = listOf("grok-3", "grok-3-thinking"),
            hookScript = genericHook("function(o){return o.result&&o.result.response&&o.result.response.token||''}")
        ),
        ProviderDef(
            id = "qwen",
            label = "Qwen",
            loginUrl = "https://chat.qwen.ai/",
            apiUrl = "https://chat.qwen.ai/api/chat/completions",
            cookieDomain = ".qwen.ai",
            sessionCookieNames = listOf("session"),
            modelIds = listOf("qwen-max", "qwen-plus"),
            hookScript = genericHook("function(o){return o.choices&&o.choices[0]&&o.choices[0].delta&&o.choices[0].delta.content||''}")
        ),
        ProviderDef(
            id = "ernie",
            label = "ERNIE Bot",
            loginUrl = "https://yiyan.baidu.com/",
            apiUrl = "https://yiyan.baidu.com/eb/chat/conversation/v2",
            cookieDomain = ".baidu.com",
            sessionCookieNames = listOf("BAIDUID", "BDUSS"),
            modelIds = listOf("ernie-4.0", "ernie-3.5"),
            hookScript = genericHook("function(o){return o.data&&o.data.content||''}")
        ),
        ProviderDef(
            id = "yuanbao",
            label = "Yuanbao",
            loginUrl = "https://yuanbao.tencent.com/chat",
            apiUrl = "https://yuanbao.tencent.com/api/chat/general",
            cookieDomain = ".tencent.com",
            sessionCookieNames = listOf("hy_token", "hy_source"),
            modelIds = listOf("hunyuan", "deepseek-r1"),
            hookScript = genericHook("function(o){return o.choices&&o.choices[0]&&o.choices[0].delta&&o.choices[0].delta.content||''}")
        ),
    )

    val byId = ALL.associateBy { it.id }
    val allModels: List<Pair<String, String>> = ALL.flatMap { p -> p.modelIds.map { it to p.id } }
}

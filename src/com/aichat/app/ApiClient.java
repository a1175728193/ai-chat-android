package com.aichat.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * OpenAI 兼容的 /chat/completions 客户端。
 * 只依赖 JDK 自带 HttpURLConnection + Android 自带 org.json，无第三方库。
 */
public final class ApiClient {

    public interface Callback {
        void onDelta(String delta);

        void onDone();

        void onError(String message);
    }

    private ApiClient() {
    }

    /** 把用户填的 Base URL 补全为 chat/completions 端点。 */
    public static String buildEndpoint(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (b.length() == 0) {
            return "";
        }
        if (b.endsWith("/chat/completions")) {
            return b;
        }
        return b + "/chat/completions";
    }

    public static void stream(String baseUrl, String apiKey, String model,
                              List<ChatMessage> messages, boolean streamMode, Callback cb) {
        HttpURLConnection conn = null;
        try {
            String endpoint = buildEndpoint(baseUrl);
            if (endpoint.length() == 0) {
                cb.onError("接口地址为空");
                return;
            }

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", streamMode);
            JSONArray arr = new JSONArray();
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage m = messages.get(i);
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("content", m.content);
                arr.put(o);
            }
            body.put("messages", arr);
            byte[] payload = body.toString().getBytes("UTF-8");

            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(180000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", streamMode ? "text/event-stream" : "application/json");
            String k = apiKey == null ? "" : apiKey.trim();
            if (k.length() > 0) {
                conn.setRequestProperty("Authorization", "Bearer " + k);
            }

            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = readAll(conn.getErrorStream());
                if (err.length() == 0) {
                    err = readAll(conn.getInputStream());
                }
                cb.onError("HTTP " + code + (err.length() == 0 ? "" : "：" + err));
                return;
            }

            if (streamMode) {
                readStream(conn.getInputStream(), cb);
                cb.onDone();
            } else {
                String text = readAll(conn.getInputStream());
                String content = extractMessage(text);
                if (content == null) {
                    cb.onError("返回内容解析失败");
                    return;
                }
                cb.onDelta(content);
                cb.onDone();
            }
        } catch (Exception e) {
            String m = e.getMessage();
            cb.onError(e.getClass().getSimpleName() + (m == null ? "" : "：" + m));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void readStream(InputStream in, Callback cb) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            String s = line.trim();
            if (s.length() == 0 || s.startsWith(":")) {
                continue;
            }
            if (s.startsWith("event:")) {
                continue;
            }
            if (s.startsWith("data:")) {
                s = s.substring(5).trim();
            }
            if (s.equals("[DONE]")) {
                break;
            }
            if (s.length() == 0 || s.charAt(0) != '{') {
                continue;
            }
            String piece = extractDelta(s);
            if (piece != null && piece.length() > 0) {
                cb.onDelta(piece);
            }
        }
        br.close();
    }

    private static String extractDelta(String json) {
        try {
            JSONObject j = new JSONObject(json);
            JSONArray ch = j.optJSONArray("choices");
            if (ch == null || ch.length() == 0) {
                return null;
            }
            JSONObject c0 = ch.optJSONObject(0);
            if (c0 == null) {
                return null;
            }
            JSONObject d = c0.optJSONObject("delta");
            if (d != null) {
                String t = d.optString("content", "");
                if (t.length() > 0) {
                    return t;
                }
            }
            JSONObject msg = c0.optJSONObject("message");
            if (msg != null) {
                return msg.optString("content", "");
            }
            return null;
        } catch (JSONException e) {
            return null;
        }
    }

    private static String extractMessage(String json) {
        try {
            JSONObject j = new JSONObject(json);
            JSONArray ch = j.optJSONArray("choices");
            if (ch != null && ch.length() > 0) {
                JSONObject c0 = ch.optJSONObject(0);
                if (c0 != null) {
                    JSONObject msg = c0.optJSONObject("message");
                    if (msg != null) {
                        return msg.optString("content", "");
                    }
                    String t = c0.optString("text", "");
                    if (t.length() > 0) {
                        return t;
                    }
                }
            }
            if (j.has("error")) {
                return "接口返回错误：" + j.opt("error");
            }
            return null;
        } catch (JSONException e) {
            return null;
        }
    }

    private static String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            String s = new String(bos.toByteArray(), "UTF-8");
            return s.length() > 600 ? s.substring(0, 600) + "…" : s;
        } catch (Exception e) {
            return "";
        }
    }
}

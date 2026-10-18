package com.aichat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<ChatMessage> history = new ArrayList<ChatMessage>();

    private LinearLayout container;
    private ScrollView scroll;
    private EditText input;
    private Button send;
    private TextView status;

    private volatile boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = (LinearLayout) findViewById(R.id.msg_container);
        scroll = (ScrollView) findViewById(R.id.scroll);
        input = (EditText) findViewById(R.id.input);
        send = (Button) findViewById(R.id.btn_send);
        status = (TextView) findViewById(R.id.text_status);

        send.setBackground(rounded(0xFF2F6FED, dp(8)));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });

        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });

        findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });

        refreshStatus();
        if (!Prefs.hasApiKey(this)) {
            addNote("尚未配置。点右上角「设置」填写接口地址、API Key 与模型名称。");
        }
    }

    // ───────────────────────── 发送 / 接收 ─────────────────────────

    private void send() {
        if (busy) {
            toast("正在生成，请稍候");
            return;
        }
        final String text = input.getText().toString().trim();
        if (text.length() == 0) {
            toast(getString(R.string.empty_input));
            return;
        }
        if (!Prefs.hasApiKey(this)) {
            toast(getString(R.string.need_key));
            openSettings();
            return;
        }

        input.setText("");
        addBubble(text, true);
        history.add(new ChatMessage("user", text));

        final TextView bubble = addBubble(getString(R.string.thinking), false);
        setBusy(true);

        final String baseUrl = Prefs.getBaseUrl(this);
        final String apiKey = Prefs.getApiKey(this);
        final String model = Prefs.getModel(this);
        final boolean streamMode = Prefs.isStream(this);
        final List<ChatMessage> payload = new ArrayList<ChatMessage>(history);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder acc = new StringBuilder();
                ApiClient.stream(baseUrl, apiKey, model, payload, streamMode, new ApiClient.Callback() {
                    @Override
                    public void onDelta(String delta) {
                        acc.append(delta);
                        final String shown = acc.toString();
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                bubble.setText(shown);
                                scrollDown();
                            }
                        });
                    }

                    @Override
                    public void onDone() {
                        final String answer = acc.toString();
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (answer.length() == 0) {
                                    bubble.setText("（未返回内容）");
                                } else {
                                    bubble.setText(answer);
                                    history.add(new ChatMessage("assistant", answer));
                                }
                                setBusy(false);
                                scrollDown();
                            }
                        });
                    }

                    @Override
                    public void onError(final String message) {
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                bubble.setTextColor(0xFFD93025);
                                bubble.setText("请求失败：" + message);
                                setBusy(false);
                                scrollDown();
                            }
                        });
                    }
                });
            }
        });
        t.start();
    }

    private void setBusy(boolean b) {
        busy = b;
        send.setEnabled(!b);
        send.setText(b ? "生成中" : getString(R.string.send));
        if (!b) {
            refreshStatus();
        }
    }

    // ───────────────────────── 气泡 UI ─────────────────────────

    private TextView addBubble(String text, boolean user) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(user ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        row.setLayoutParams(rlp);

        final TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15f);
        tv.setTextColor(user ? 0xFFFFFFFF : 0xFF1A1D21);
        int p = dp(12);
        tv.setPadding(p, dp(9), p, dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(user ? 0xFF2F6FED : 0xFFFFFFFF);
        bg.setCornerRadius(dp(14));
        if (!user) {
            bg.setStroke(dp(1), 0xFFE8EAEE);
        }
        tv.setBackground(bg);
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.80f));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                copy(tv.getText().toString());
                return true;
            }
        });

        row.addView(tv);
        container.addView(row);
        scrollDown();
        return tv;
    }

    private void addNote(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(0xFF8A9099);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        tv.setLayoutParams(lp);
        container.addView(tv);
        scrollDown();
    }

    private void scrollDown() {
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    // ───────────────────────── 设置 / 清空 ─────────────────────────

    private void openSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        box.setPadding(p, dp(6), p, 0);

        final EditText etBase = new EditText(this);
        etBase.setHint(R.string.base_url_hint);
        etBase.setText(Prefs.getBaseUrl(this));
        etBase.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(etBase);

        final EditText etKey = new EditText(this);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (Prefs.hasApiKey(this)) {
            etKey.setHint("API Key（已保存，留空表示不修改）");
        } else {
            etKey.setHint(R.string.api_key_hint);
        }
        box.addView(etKey);

        final EditText etModel = new EditText(this);
        etModel.setHint(R.string.model_hint);
        etModel.setText(Prefs.getModel(this));
        box.addView(etModel);

        final CheckBox cb = new CheckBox(this);
        cb.setText(R.string.stream_on);
        cb.setChecked(Prefs.isStream(this));
        box.addView(cb);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(box)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String base = etBase.getText().toString().trim();
                        String key = etKey.getText().toString().trim();
                        String model = etModel.getText().toString().trim();
                        if (base.length() > 0) {
                            Prefs.setBaseUrl(MainActivity.this, base);
                        }
                        if (key.length() > 0) {
                            Prefs.setApiKey(MainActivity.this, key);
                        }
                        if (model.length() > 0) {
                            Prefs.setModel(MainActivity.this, model);
                        }
                        Prefs.setStream(MainActivity.this, cb.isChecked());
                        refreshStatus();
                        toast("已保存");
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_title)
                .setMessage(R.string.clear_msg)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        history.clear();
                        container.removeAllViews();
                        addNote("对话已清空，开始新的一轮吧。");
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void refreshStatus() {
        String key = Prefs.hasApiKey(this) ? "已配置 Key" : "未配置 Key";
        status.setText(Prefs.getModel(this) + " · " + ApiClient.buildEndpoint(Prefs.getBaseUrl(this)) + " · " + key);
    }

    // ───────────────────────── 工具 ─────────────────────────

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("chat", text));
                toast("已复制");
            }
        } catch (Exception ignored) {
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}

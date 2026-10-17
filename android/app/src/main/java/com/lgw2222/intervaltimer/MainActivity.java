package com.lgw2222.intervaltimer;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Hosts the same timer page used on the web, and adds what a web page can't do:
 * the timer shrinks into a floating window on its own whenever you switch apps mid-workout.
 */
public class MainActivity extends Activity {

    private static final String ACTION_CMD = "com.lgw2222.intervaltimer.CMD";

    private WebView web;
    private boolean running = false;   // a workout is in progress (not finished)
    private boolean paused = false;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#14204A"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);

        web.addJavascriptInterface(new Bridge(), "AndroidTimer");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });

        if (saved != null) web.restoreState(saved);
        else web.loadUrl("file:///android_asset/index.html");

        // Buttons in the floating window send commands back to the page.
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String cmd = i.getStringExtra("cmd");
                if (cmd != null) js("window.__nativeCmd && window.__nativeCmd('" + cmd + "')");
            }
        };
        IntentFilter f = new IntentFilter(ACTION_CMD);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);

        updatePip();
    }

    private void js(String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    private PendingIntent cmdIntent(String cmd, int req) {
        Intent i = new Intent(ACTION_CMD).setPackage(getPackageName()).putExtra("cmd", cmd);
        return PendingIntent.getBroadcast(this, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PictureInPictureParams buildParams() {
        List<RemoteAction> actions = new ArrayList<>();
        actions.add(new RemoteAction(Icon.createWithResource("android", android.R.drawable.ic_media_previous),
                "Back", "Back", cmdIntent("prev", 1)));
        int playIcon = paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause;
        actions.add(new RemoteAction(Icon.createWithResource("android", playIcon),
                paused ? "Resume" : "Pause", paused ? "Resume" : "Pause", cmdIntent("toggle", 2)));
        actions.add(new RemoteAction(Icon.createWithResource("android", android.R.drawable.ic_media_next),
                "Skip", "Skip", cmdIntent("next", 3)));

        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(4, 3))
                .setActions(actions);
        if (Build.VERSION.SDK_INT >= 31) {
            b.setAutoEnterEnabled(running);   // swipe home / switch apps -> floating window
            b.setSeamlessResizeEnabled(false);
        }
        return b.build();
    }

    private void updatePip() {
        try { setPictureInPictureParams(buildParams()); } catch (Exception ignored) {}
    }

    private void enterPip() {
        try { enterPictureInPictureMode(buildParams()); } catch (Exception ignored) {}
    }

    // Older Android (before 12): enter the floating window when you leave the app.
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (running && Build.VERSION.SDK_INT < 31) enterPip();
    }

    @Override
    public void onBackPressed() {
        if (running) enterPip();
        else super.onBackPressed();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        js("window.__setPip && window.__setPip(" + inPip + ")");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    /** Methods the timer page can call. */
    private class Bridge {
        @JavascriptInterface
        public void setState(boolean isRunning, boolean isPaused) {
            runOnUiThread(() -> {
                running = isRunning;
                paused = isPaused;
                if (running) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                updatePip();
            });
        }

        @JavascriptInterface
        public void setBars(String hex, boolean lightIcons) {
            runOnUiThread(() -> {
                try {
                    int c = Color.parseColor(hex);
                    getWindow().setStatusBarColor(c);
                    getWindow().setNavigationBarColor(c);
                    View d = getWindow().getDecorView();
                    int flags = d.getSystemUiVisibility();
                    int dark = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    d.setSystemUiVisibility(lightIcons ? (flags & ~dark) : (flags | dark));
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void vibrate(String json) {
            try {
                JSONArray a = new JSONArray(json);
                long[] t = new long[a.length() + 1];
                t[0] = 0;
                for (int i = 0; i < a.length(); i++) t[i + 1] = a.getLong(i);
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(VibrationEffect.createWaveform(t, -1));
            } catch (Exception ignored) {}
        }
    }
}

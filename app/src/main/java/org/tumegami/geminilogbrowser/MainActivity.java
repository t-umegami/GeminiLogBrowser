package org.tumegami.geminilogbrowser;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient; // WebChromeClientをインポート
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Handler handler = new Handler();
    private Runnable logRunnable;
    private String currentLogFileName = "";
    private static final String DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // WebViewのデバッグを有効にする
        WebView.setWebContentsDebuggingEnabled(true);

        webView = findViewById(R.id.webView);
        webView.setWebViewClient(new MyWebViewClient()); // 独立したWebViewClientを設定
        webView.setWebChromeClient(new MyWebChromeClient()); // 独立したWebChromeClientを設定

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // DOMストレージを有効化
        webSettings.setDatabaseEnabled(true); // データベースAPIを有効化
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT); // キャッシュモードを設定
        webSettings.setUserAgentString(DESKTOP_USER_AGENT); // PC用ユーザーエージェントを設定

        // レンダリング優先度を高く設定
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);

        // ハードウェアアクセラレーションを明示的に有効にする
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);

        // WebViewのキャッシュと履歴をクリア
        webView.clearCache(true);
        webView.clearHistory();

        // ナビゲーション設定を明示的に行う
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);

        // プロセス分離を無効にする (API 28以上で推奨されないが、デバッグ目的で試す)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            webView.setWebViewRenderProcessClient(null);
        }

        // JavaScriptインターフェースを追加
        webView.addJavascriptInterface(new JavaScriptInterface(), "Android");

        // 初期URLをgemini.google.comに設定
        webView.loadUrl("https://gemini.google.com/");
    }

    private void updateLogFileName(String url) {
        if (url == null) return;
        Pattern pattern = Pattern.compile("chat/([^?]+)");
        Matcher matcher = pattern.matcher(url);
        String conversationId = null;
        if (matcher.find()) {
            conversationId = matcher.group(1);
        }

        String newLogFileName;
        if (conversationId != null && !conversationId.isEmpty()) {
            newLogFileName = "gemini_chat_" + conversationId + ".txt";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            newLogFileName = "gemini_log_" + sdf.format(new Date()) + ".txt";
        }

        if (!newLogFileName.equals(currentLogFileName)) {
            System.out.println("Log file name updated to: " + newLogFileName);
            currentLogFileName = newLogFileName;
        }
    }

    private void startLogging() {
        if (logRunnable != null) {
            handler.removeCallbacks(logRunnable);
        }

        logRunnable = new Runnable() {
            @Override
            public void run() {
                // WebViewのコンテンツとURLをJavaScriptで取得
                webView.evaluateJavascript(
                        "(function() { return { content: document.body.innerText, url: document.URL }; })();",
                        value -> {
                            System.out.println("evaluateJavascript result: " + value);
                            if (value != null && !value.equals("null") && !value.isEmpty()) {
                                try {
                                    org.json.JSONObject json = new org.json.JSONObject(value);
                                    String content = json.getString("content");
                                    String url = json.getString("url");
                                    logToFile("Content", content);
                                    logToFile("URL", url);
                                } catch (org.json.JSONException e) {
                                    e.printStackTrace();
                                    System.err.println("JSON parsing error: " + e.getMessage());
                                }
                            } else {
                                System.out.println("evaluateJavascript returned null or empty value.");
                            }
                        });
                handler.postDelayed(this, 5000); // 5秒ごとにログを記録
            }
        };
        handler.post(logRunnable);
    }

    private void logToFile(String tag, String content) {
        if (currentLogFileName.isEmpty()) {
            // ファイル名がまだ設定されていない場合はデフォルト名で初期化
            updateLogFileName(webView.getUrl());
        }

        File logDir = getExternalFilesDir(null);
        if (logDir == null) {
            System.err.println("External storage not available.");
            return;
        }
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                System.err.println("Failed to create log directory.");
                return;
            }
        }

        File logFile = new File(logDir, currentLogFileName);
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) { // 追記モード
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            writer.println("[" + sdf.format(new Date()) + "] [" + tag + "]");
            writer.println(content);
            writer.println(); // 空行を追加
            writer.flush();
            System.out.println("Log written to: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error writing log to file: " + e.getMessage());
        }
    }

    // WebViewからJavaコードを呼び出すためのJavaScriptインターフェース
    public class JavaScriptInterface {
        @JavascriptInterface
        public void showToast(String message) {
            // This is a sample.
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logRunnable != null) {
            handler.removeCallbacks(logRunnable);
        }
    }

    // 独立したWebViewClientクラス
    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            System.out.println("onPageStarted: " + url);
            logToFile("URL", "onPageStarted: " + url);
            updateLogFileName(url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            System.out.println("shouldOverrideUrlLoading: " + url);
            logToFile("URL", "shouldOverrideUrlLoading: " + url);
            // Let the WebView handle the URL loading
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            System.out.println("onPageFinished: " + url);
            logToFile("URL", "onPageFinished: " + url);
            // Start logging when the page is finished loading
            startLogging();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            System.err.println("onReceivedError: " + errorCode + ", " + description + ", " + failingUrl);
            logToFile("Error", "Code: " + errorCode + ", Desc: " + description + ", URL: " + failingUrl);
        }
    }

    // 独立したWebChromeClientクラス
    private class MyWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            System.out.println("onProgressChanged: " + newProgress + "%");
            logToFile("Progress", String.valueOf(newProgress));
        }

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
            String message = "Console: " + consoleMessage.message() + " -- From line "
                    + consoleMessage.lineNumber() + " of "
                    + consoleMessage.sourceId();
            System.out.println(message);
            logToFile("Console", message);
            return super.onConsoleMessage(consoleMessage);
        }
    }
}
package kr.luckykoopa.shuttle;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * 꿈에그린 셔틀 시간표 웹페이지를 감싸는 WebView 앱.
 * 집 안(LAN) 주소 → Tailscale 주소 순으로 시도하고, 둘 다 안 되면 앱에 내장된 같은 페이지를 연다
 * (오프라인에서도 시간표를 볼 수 있음. 내장본은 빌드 시점의 시간표).
 */
public class MainActivity extends Activity {
    private static final String[] URLS = {
        "http://192.168.100.140:8782/",
        "http://100.86.158.27:8782/",
    };
    private static final String LOCAL = "file:///android_asset/web/index.html";
    private static final String[] OUR_HOSTS = {"192.168.100.140", "100.86.158.27"};

    private WebView web;
    private SwipeRefreshLayout swipe;
    private int urlIndex = 0;
    private boolean loadedOnce = false;   // 서버 페이지를 한 번이라도 성공적으로 열었는가
    private boolean errorThisLoad = false;
    private long lastBackMs = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long ATTEMPT_TIMEOUT_MS = 4000;   // 주소 하나당 연결 대기 (LAN 밖에서 사설 IP 가 오래 매달리지 않도록)
    private final Runnable attemptTimeout = () -> { web.stopLoading(); nextOrLocal(); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#1f5f8b"));
        swipe = new SwipeRefreshLayout(this);
        swipe.setColorSchemeColors(Color.parseColor("#1f5f8b"));
        web = new WebView(this);
        swipe.addView(web, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(swipe);
        swipe.setOnChildScrollUpCallback((parent, child) -> web.getScrollY() > 0);
        // 당겨서 새로고침: 항상 서버부터 다시 시도 (내장본을 보고 있어도 서버가 되면 최신 시간표로)
        swipe.setOnRefreshListener(this::loadFromServer);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(true);
        s.setUserAgentString(s.getUserAgentString() + " ShuttleApp");   // 페이지가 앱 안인지 알 수 있게 (APK 링크 숨김)

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (isOurs(u)) return false;
                openExternal(u);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (!req.isForMainFrame()) return;
                errorThisLoad = true;
                handler.removeCallbacks(attemptTimeout);
                if (!loadedOnce) nextOrLocal();
                swipe.setRefreshing(false);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                handler.removeCallbacks(attemptTimeout);
                // 실패한 로드도 onPageFinished 가 오므로, 오류 없이 끝난 서버 페이지만 성공으로 본다
                if (!errorThisLoad && url != null && url.startsWith("http")) loadedOnce = true;
                swipe.setRefreshing(false);
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState); else loadFromServer();
    }

    private void loadFromServer() {
        urlIndex = 0;
        loadedOnce = false;
        attempt(URLS[0]);
    }

    private void attempt(String url) {
        errorThisLoad = false;
        handler.removeCallbacks(attemptTimeout);
        if (url.startsWith("http")) handler.postDelayed(attemptTimeout, ATTEMPT_TIMEOUT_MS);
        web.loadUrl(url);
    }

    /** 다음 서버 주소로, 모두 실패했으면 앱에 내장된 시간표로. */
    private void nextOrLocal() {
        if (urlIndex + 1 < URLS.length) {
            urlIndex++;
            attempt(URLS[urlIndex]);
        } else {
            Toast.makeText(this, "서버에 연결할 수 없어 저장된 시간표를 표시합니다", Toast.LENGTH_SHORT).show();
            attempt(LOCAL);
        }
    }

    private boolean isOurs(Uri u) {
        String scheme = u.getScheme();
        if (scheme == null || scheme.equals("file")) return true;
        String h = u.getHost();
        if (h == null) return true;
        for (String o : OUR_HOSTS) if (o.equals(h)) return true;
        return false;
    }

    private void openExternal(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
    }

    /** 뒤로 가기: 페이지 히스토리(원본 사진 등)가 있으면 뒤로, 아니면 한 번 더 누르면 종료. */
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) { web.goBack(); return; }
        long now = System.currentTimeMillis();
        if (now - lastBackMs < 2000) {
            finish();
            return;
        }
        lastBackMs = now;
        Toast.makeText(this, "한 번 더 누르면 앱을 종료합니다", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(attemptTimeout);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }
}

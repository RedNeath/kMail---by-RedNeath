package com.redneath.kmel;

import static com.redneath.kmel.PageSource.*;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;

import java.util.Stack;

public class MainActivity extends Activity {
    public static String url = "https://mail.infomaniak.com/";
    public static String lastIkUrl = "https://mail.infomaniak.com/";
    public static Stack<String> lastUrls = new Stack<String>();
    public static Stack<String> nextUrls = new Stack<String>();

    public static Toolbar toolbar;
    public static TextView pageName;
    public static TextView pageUrl;

    public static int exitBacks = 0;

    @SuppressLint({"SetTextI18n", "SetJavaScriptEnabled"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_layout);

        WebView view = findViewById(R.id.view);
        ImageButton back = findViewById(R.id.back_arrow);
        EditText research = findViewById(R.id.search);
        toolbar = findViewById(R.id.toolbar);
        pageName = findViewById(R.id.page_name);
        pageUrl = findViewById(R.id.page_url);

        WebSettings params = view.getSettings();
        params.setJavaScriptEnabled(true);
        params.setBuiltInZoomControls(true);
        params.setDisplayZoomControls(false);

        params.setDomStorageEnabled(true);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                MainActivity.loadPage(view, request.getUrl().toString());
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                MainActivity.setDisplayToolbar();
                if (MainActivity.isInfomaniakUrl(url) &&
                    !MainActivity.isInfomaniakUrl(MainActivity.lastUrls.peek())
                   ) {
                    MainActivity.lastUrls = new Stack<String>();
                }

                MainActivity.pageName.setText(view.getTitle());
                MainActivity.pageUrl.setText(view.getUrl());
            }
        });

        view.setDownloadListener(new DownloadListener()
        {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimeType,
                                        long contentLength) {
                DownloadManager.Request request = new DownloadManager.Request(
                        Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading File...");
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(
                                url, contentDisposition, mimeType));
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_LONG).show();
            }});

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WebView wview = findViewById(R.id.view);
                MainActivity.loadPage(wview, MainActivity.lastIkUrl);
            }
        });

        research.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                //DO NOTHING
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                View globalView = findViewById(R.id.global_view);
                MainActivity.highlightMatchesAsync(globalView);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                //DO NOTHING
            }
        });

        loadPage(view, url);
    }

    @Override
    public void onBackPressed() {
        if (lastUrls.empty() && exitBacks < 1) {
            exitBacks++;

            Toast exit = new Toast(this);
            exit.setText("Press once more to quit");
            exit.setDuration(Toast.LENGTH_SHORT);
            exit.show();
        } else if (lastUrls.empty() && exitBacks == 1) {
            super.onBackPressed();
        } else {
            navigateBackwards(null);
        }
    }

    public void showPopup(View menuOpenButton) {
        PopupMenu popup = new PopupMenu(this, menuOpenButton);
        Menu menu = popup.getMenu();

        popup.getMenuInflater().inflate(R.menu.actions, menu);
        popup.show();
    }

    public void showSearch(MenuItem item) {
        View standardLayout = findViewById(R.id.stdToolbarDisplay);
        View searchLayout = findViewById(R.id.searchToolbarDisplay);
        View globalView = findViewById(R.id.global_view);

        standardLayout.setVisibility(View.GONE);
        searchLayout.setVisibility(View.VISIBLE);

        highlightMatchesAsync(globalView);
    }

    public void hideSearch(View item) {
        View standardLayout = findViewById(R.id.stdToolbarDisplay);
        View searchLayout = findViewById(R.id.searchToolbarDisplay);
        WebView view = findViewById(R.id.view);

        standardLayout.setVisibility(View.VISIBLE);
        searchLayout.setVisibility(View.GONE);

        view.clearMatches();
    }

    public void findPrevious(View item) {
        WebView view = findViewById(R.id.view);
        EditText research = findViewById(R.id.search);

        if (!research.getText().toString().equals("")) {
            view.findNext(false);
        }
    }

    public void findNext(View item) {
        WebView view = findViewById(R.id.view);
        EditText research = findViewById(R.id.search);

        if (!research.getText().toString().equals("")) {
            view.findNext(true);
        }
    }

    public void navigateBackwards(MenuItem item) {
        WebView view = findViewById(R.id.view);
        loadPage(view, "", FROM_LAST_STACK);
    }

    public void navigateForwards(MenuItem item) {
        WebView view = findViewById(R.id.view);
        loadPage(view, "", FROM_NEXT_STACK);
    }

    public void refreshPage(MenuItem item) {
        WebView view = findViewById(R.id.view);
        view.reload();
    }

    public void sharePage(MenuItem item) {
        WebView view = findViewById(R.id.view);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("kMail link", view.getUrl());
        clipboard.setPrimaryClip(clip);

        Toast result = new Toast(this);
        result.setText("Link copied to clipboard");
        result.setDuration(Toast.LENGTH_SHORT);
        result.show();
    }

    public static void loadPage(WebView view, String url) {
        loadPage(view, url, UNKNOWN);
    }

    public static void loadPage(WebView view, String url, PageSource source) {
        //  resetting the exit backs when changing page
        exitBacks = 0;

        switch (source) {
            case UNKNOWN:
                lastUrls.push(MainActivity.url);
                nextUrls = new Stack<String>();

                affectNewUrl(view, url);
                break;

            case FROM_LAST_STACK:
                if (lastUrls.empty()) {
                    break;
                }

                url = lastUrls.pop();
                nextUrls.push(MainActivity.url);

                affectNewUrl(view, url);
                break;

            case FROM_NEXT_STACK:
                if (nextUrls.empty()) {
                    break;
                }

                lastUrls.push(MainActivity.url);
                url = nextUrls.pop();

                affectNewUrl(view, url);
                break;
        }
    }

    public static void affectNewUrl(WebView view, String url) {
        MainActivity.url = url;
        view.loadUrl(MainActivity.url);
        MainActivity.updateLastIkUrl();
    }

    public static boolean isInfomaniakUrl(String url) {
        return url.toLowerCase().contains("infomaniak");
    }

    public static void updateLastIkUrl() {
        if (isInfomaniakUrl(url)) {
            lastIkUrl = url;
        }
    }

    public static void setDisplayToolbar() {
        if (isInfomaniakUrl(url)) {
            toolbar.setVisibility(View.GONE);
        } else {
            toolbar.setVisibility(View.VISIBLE);
        }
    }

    public static void highlightMatchesAsync(View view) {
        WebView wview = view.findViewById(R.id.view);
        EditText research = view.findViewById(R.id.search);
        wview.findAllAsync(research.getText().toString());
    }
}

package com.moi.atmoz;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  private volatile float sat = 0f;
  private volatile float sab = 0f;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final Window w = getWindow();
    WindowCompat.setDecorFitsSystemWindows(w, false);
    w.setStatusBarColor(Color.TRANSPARENT);
    w.setNavigationBarColor(Color.TRANSPARENT);
    if (Build.VERSION.SDK_INT >= 29) { w.setNavigationBarContrastEnforced(false); }
    if (Build.VERSION.SDK_INT >= 28) {
      w.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }
    w.setBackgroundDrawable(new ColorDrawable(0xFF050505));
    WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
    c.setAppearanceLightStatusBars(false);
    c.setAppearanceLightNavigationBars(false);

    final WebView wv = getBridge().getWebView();
    wv.addJavascriptInterface(new Object() {
      @JavascriptInterface
      public String insets() { return sat + "," + sab; }

      @JavascriptInterface
      public void refreshWidgets() {
        Context ctx = getApplicationContext();
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        Class<?>[] cls = { SmallWidget.class, WideWidget.class, ForecastWidget.class };
        for (Class<?> k : cls) {
          int[] ids = m.getAppWidgetIds(new ComponentName(ctx, k));
          if (ids != null && ids.length > 0) {
            Intent i = new Intent(ctx, k);
            i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            ctx.sendBroadcast(i);
          }
        }
      }
    }, "AtmozNative");

    ViewCompat.setOnApplyWindowInsetsListener(w.getDecorView(), (v, insets) -> {
      Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
      float d = getResources().getDisplayMetrics().density;
      sat = i.top / d;
      sab = i.bottom / d;
      final String js = "try{var s=document.documentElement.style;s.setProperty('--sat','" + sat + "px');s.setProperty('--sab','" + sab + "px');}catch(e){}";
      wv.post(() -> wv.evaluateJavascript(js, null));
      return insets;
    });
  }
}

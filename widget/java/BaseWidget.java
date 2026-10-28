package com.moi.atmoz;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public abstract class BaseWidget extends AppWidgetProvider {
  abstract int layout();
  abstract boolean wide();
  boolean forecast() { return false; }

  @Override
  public void onUpdate(final Context c, final AppWidgetManager m, final int[] ids) {
    final PendingResult pr = goAsync();
    new Thread(new Runnable() {
      public void run() {
        try {
          RemoteViews v = build(c);
          if (v != null) { for (int id : ids) m.updateAppWidget(id, v); }
        } catch (Exception e) {
        } finally { pr.finish(); }
      }
    }).start();
  }

  static String get(String u) throws Exception {
    HttpURLConnection k = (HttpURLConnection) new URL(u).openConnection();
    k.setConnectTimeout(8000);
    k.setReadTimeout(8000);
    BufferedReader r = new BufferedReader(new InputStreamReader(k.getInputStream()));
    StringBuilder b = new StringBuilder();
    String l;
    while ((l = r.readLine()) != null) b.append(l);
    r.close();
    return b.toString();
  }

  static String ico(int c, boolean day) {
    if (c == 0 || c == 1) return day ? "\u2600\uFE0F" : "\uD83C\uDF19";
    if (c == 2) return day ? "\u26C5" : "\u2601\uFE0F";
    if (c == 3) return "\u2601\uFE0F";
    if (c == 45 || c == 48) return "\uD83C\uDF2B\uFE0F";
    if ((c >= 51 && c <= 57) || (c >= 80 && c <= 82)) return day ? "\uD83C\uDF26\uFE0F" : "\uD83C\uDF27\uFE0F";
    if (c >= 61 && c <= 67) return "\uD83C\uDF27\uFE0F";
    if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "\uD83C\uDF28\uFE0F";
    if (c >= 95) return "\u26C8\uFE0F";
    return "\uD83C\uDF21\uFE0F";
  }

  static String label(int c, boolean day) {
    if (c == 0) return day ? "Journ\u00E9e d\u00E9gag\u00E9e" : "Nuit d\u00E9gag\u00E9e";
    if (c == 1) return "Plut\u00F4t d\u00E9gag\u00E9";
    if (c == 2) return "Partiellement nuageux";
    if (c == 3) return "Couvert";
    if (c == 45 || c == 48) return "Brouillard";
    if (c >= 51 && c <= 57) return "Bruine";
    if (c >= 61 && c <= 67) return "Pluie";
    if (c >= 71 && c <= 77) return "Neige";
    if (c >= 80 && c <= 82) return "Averses";
    if (c == 85 || c == 86) return "Averses de neige";
    if (c >= 95) return "Orage";
    return "Variable";
  }

  static CharSequence col(String top, String icon, String bottom) {
    SpannableStringBuilder b = new SpannableStringBuilder();
    b.append(top).append("\n");
    int s = b.length();
    b.append(icon);
    b.setSpan(new RelativeSizeSpan(1.7f), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    b.append("\n");
    s = b.length();
    b.append(bottom);
    b.setSpan(new RelativeSizeSpan(0.85f), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    return b;
  }

  RemoteViews build(Context c) {
    RemoteViews v = new RemoteViews(c.getPackageName(), layout());
    try {
      Intent li = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
      if (li != null) {
        v.setOnClickPendingIntent(R.id.root,
          PendingIntent.getActivity(c, 0, li, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
      }
      String raw = c.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE).getString("home", null);
      if (raw == null) { v.setTextViewText(R.id.city, "Ouvrez Atmoz"); return v; }
      JSONObject p = new JSONObject(raw);
      JSONObject j = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + p.getDouble("lat")
        + "&longitude=" + p.getDouble("lon")
        + "&current=temperature_2m,apparent_temperature,weather_code,is_day&hourly=temperature_2m,weather_code,is_day"
        + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto"));
      JSONObject cur = j.getJSONObject("current");
      JSONObject h = j.getJSONObject("hourly");
      JSONObject d = j.getJSONObject("daily");
      boolean day = cur.getInt("is_day") == 1;
      v.setTextViewText(R.id.city, p.optString("name", "Ma position"));
      v.setTextViewText(R.id.icon, ico(cur.getInt("weather_code"), day));
      v.setTextViewText(R.id.temp, Math.round(cur.getDouble("temperature_2m")) + "\u00B0");
      v.setTextViewText(R.id.range, "\u2191" + Math.round(d.getJSONArray("temperature_2m_max").getDouble(0))
        + "\u00B0  \u2193" + Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)) + "\u00B0");
      if (wide()) {
        JSONArray t = h.getJSONArray("time");
        JSONArray tt = h.getJSONArray("temperature_2m");
        JSONArray wc = h.getJSONArray("weather_code");
        JSONArray dd = h.getJSONArray("is_day");
        String now = cur.getString("time").substring(0, 13);
        int i0 = 0;
        for (int i = 0; i < t.length(); i++) { if (t.getString(i).startsWith(now)) { i0 = i; break; } }
        int[] hid = { R.id.h0, R.id.h1, R.id.h2, R.id.h3, R.id.h4 };
        for (int k = 0; k < 5; k++) {
          int i = i0 + 1 + k;
          if (i >= t.length()) break;
          v.setTextViewText(hid[k], t.getString(i).substring(11, 13) + "h\n"
            + ico(wc.getInt(i), dd.getInt(i) == 1) + "\n" + Math.round(tt.getDouble(i)) + "\u00B0");
        }
      }
      if (forecast()) {
        v.setTextViewText(R.id.cond, label(cur.getInt("weather_code"), day));
        v.setTextViewText(R.id.feels, "Ressenti : " + Math.round(cur.getDouble("apparent_temperature")) + "\u00B0");
        v.setTextViewText(R.id.range, Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)) + "\u00B0 \u00B7 "
          + Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)) + "\u00B0");
        JSONArray t = h.getJSONArray("time");
        JSONArray tt = h.getJSONArray("temperature_2m");
        JSONArray wc = h.getJSONArray("weather_code");
        JSONArray dd = h.getJSONArray("is_day");
        String now = cur.getString("time").substring(0, 13);
        int i0 = 0;
        for (int i = 0; i < t.length(); i++) { if (t.getString(i).startsWith(now)) { i0 = i; break; } }
        int[] fid = { R.id.f0, R.id.f1, R.id.f2, R.id.f3, R.id.f4, R.id.f5, R.id.f6 };
        v.setTextViewText(fid[0], col(Math.round(cur.getDouble("temperature_2m")) + "\u00B0",
          ico(cur.getInt("weather_code"), day), "Maintenant"));
        for (int k = 1; k < 7; k++) {
          int i = i0 + k;
          if (i >= t.length()) break;
          v.setTextViewText(fid[k], col(Math.round(tt.getDouble(i)) + "\u00B0",
            ico(wc.getInt(i), dd.getInt(i) == 1), t.getString(i).substring(11, 13) + " h"));
        }
      }
      return v;
    } catch (Exception e) {
      return null;
    }
  }
}

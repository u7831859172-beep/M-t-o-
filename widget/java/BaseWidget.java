package com.moi.atmoz;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public abstract class BaseWidget extends AppWidgetProvider {
  abstract int layout();
  int cols() { return 0; }
  boolean cond() { return false; }
  boolean feels() { return false; }

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

  static int icon(int c, boolean day) {
    if (c == 0 || c == 1) return day ? R.drawable.w_sun : R.drawable.w_moon;
    if (c == 2) return day ? R.drawable.w_partly : R.drawable.w_night_partly;
    if (c == 45 || c == 48) return R.drawable.w_fog;
    if ((c >= 51 && c <= 67) || (c >= 80 && c <= 82)) return R.drawable.w_rain;
    if ((c >= 71 && c <= 77) || c == 85 || c == 86) return R.drawable.w_snow;
    if (c >= 95) return R.drawable.w_storm;
    return R.drawable.w_cloud;
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

  static int vid(Context c, String name) {
    return c.getResources().getIdentifier(name, "id", c.getPackageName());
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
      int code = cur.getInt("weather_code");
      String tnow = Math.round(cur.getDouble("temperature_2m")) + "\u00B0";
      v.setTextViewText(R.id.city, p.optString("name", "Ma position"));
      v.setImageViewResource(R.id.icon, icon(code, day));
      v.setTextViewText(R.id.temp, tnow);
      v.setTextViewText(R.id.range, Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)) + "\u00B0 \u00B7 "
        + Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)) + "\u00B0");
      if (cond()) v.setTextViewText(R.id.cond, label(code, day));
      if (feels()) v.setTextViewText(R.id.feels, "Ressenti : " + Math.round(cur.getDouble("apparent_temperature")) + "\u00B0");
      int n = cols();
      if (n > 0) {
        JSONArray t = h.getJSONArray("time");
        JSONArray tt = h.getJSONArray("temperature_2m");
        JSONArray wc = h.getJSONArray("weather_code");
        JSONArray dd = h.getJSONArray("is_day");
        String now = cur.getString("time").substring(0, 13);
        int i0 = 0;
        for (int i = 0; i < t.length(); i++) { if (t.getString(i).startsWith(now)) { i0 = i; break; } }
        for (int k = 0; k < n; k++) {
          String top, bottom;
          int res;
          if (k == 0) { top = tnow; res = icon(code, day); bottom = "Maintenant"; }
          else {
            int i = i0 + k;
            if (i >= t.length()) break;
            top = Math.round(tt.getDouble(i)) + "\u00B0";
            res = icon(wc.getInt(i), dd.getInt(i) == 1);
            bottom = t.getString(i).substring(11, 13) + " h";
          }
          v.setTextViewText(vid(c, "ct" + k), top);
          v.setImageViewResource(vid(c, "ci" + k), res);
          v.setTextViewText(vid(c, "cl" + k), bottom);
        }
      }
      return v;
    } catch (Exception e) {
      return null;
    }
  }
}

package com.moi.atmoz;

public class ForecastWidget extends BaseWidget {
  int layout() { return R.layout.widget_forecast; }
  boolean wide() { return false; }
  boolean forecast() { return true; }
}

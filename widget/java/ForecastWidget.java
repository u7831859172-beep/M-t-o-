package com.moi.atmoz;

public class ForecastWidget extends BaseWidget {
  int layout() { return R.layout.widget_forecast; }
  int cols() { return 7; }
  boolean cond() { return true; }
  boolean feels() { return true; }
}

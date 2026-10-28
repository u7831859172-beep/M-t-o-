package com.moi.atmoz;

public class WideWidget extends BaseWidget {
  int layout() { return R.layout.widget_wide; }
  int cols() { return 5; }
  boolean cond() { return true; }
}

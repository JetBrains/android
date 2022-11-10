package com.android.tools.screensharing;

import android.hardware.display.DisplayManager;

public class DisplayListener implements DisplayManager.DisplayListener {
  @Override
  public native void onDisplayAdded(int displayId);

  @Override
  public native void onDisplayRemoved(int displayId);

  @Override
  public native void onDisplayChanged(int displayId);
}

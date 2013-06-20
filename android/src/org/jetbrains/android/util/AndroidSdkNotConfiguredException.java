package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkNotConfiguredException extends Exception {
  public AndroidSdkNotConfiguredException() {
  }

  public AndroidSdkNotConfiguredException(@NotNull String message) {
    super(message);
  }
}

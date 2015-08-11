package org.jetbrains.android.logcat;

import com.intellij.util.ArrayUtil;
import net.jcip.annotations.ThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A thread-safe collection of error strings reported by Android logcat.
 */
@ThreadSafe
public final class AdbErrors {
  private AdbErrors() {
  }

  private static volatile List<String> ourAdbErrorList;

  @NotNull
  public static synchronized String[] getErrors() {
    return ourAdbErrorList != null ? ArrayUtil.toStringArray(ourAdbErrorList) : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public static synchronized void clear() {
    if (ourAdbErrorList != null) {
      ourAdbErrorList.clear();
    }
  }

  public static synchronized void reportError(@NotNull String message, @Nullable String tag) {
    final String fullMessage = tag != null ? tag + ": " + message : message;
    if (ourAdbErrorList == null) {
      ourAdbErrorList = new ArrayList<String>();
    }
    ourAdbErrorList.add(fullMessage);
  }
}

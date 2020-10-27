package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

public interface OutputProcessor {
  void onTextAvailable(@NotNull String text);
}

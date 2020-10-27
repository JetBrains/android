package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

public interface ErrorReporter {
  void report(@NotNull String message, @NotNull String title);
}

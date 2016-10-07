package com.android.tools.idea.stats;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * {ILogger} implementation that forwards logs to the IntelliJ {@link Logger}.
 */
public class IntelliJLogger implements ILogger {

  private final Logger myLogger;

  /**
   * Creates an instance of the {@link IntelliJLogger}, using the provided class to pass on to the {@link Logger}.
   */
  public IntelliJLogger(Class clz) {
    myLogger = Logger.getInstance(clz);
  }

  /**
   * Creates an instance of the {@link IntelliJLogger}, using the provided {@link Logger}.
   */
  public IntelliJLogger(Logger logger) { myLogger = logger;  }

  @Override
  public void error(@org.jetbrains.annotations.Nullable Throwable t, @com.android.annotations.Nullable String msgFormat, Object... args) {
    myLogger.error(String.format(msgFormat, args), t);
  }

  @Override
  public void warning(@NotNull String msgFormat, Object... args) {
    myLogger.warn(String.format(msgFormat, args));
  }

  @Override
  public void info(@NotNull String msgFormat, Object... args) {
    myLogger.info(String.format(msgFormat, args));
  }

  @Override
  public void verbose(@NotNull String msgFormat, Object... args) {
    info(msgFormat, args);
  }
}

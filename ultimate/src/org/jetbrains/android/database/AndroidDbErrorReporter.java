package org.jetbrains.android.database;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

class AndroidDbErrorReporter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDbErrorReporter");

  private volatile boolean myHasError;

  public synchronized void reportError(@NotNull String message) {
    myHasError = true;
  }

  public synchronized boolean hasError() {
    return myHasError;
  }

  public void reportError(@NotNull Exception exception) {
    final String exceptionMessage = exception.getMessage();
    final String suffix = exceptionMessage != null && exceptionMessage.length() > 0 ? (": " + exceptionMessage) : "";
    reportError(exception.getClass().getSimpleName() + suffix);
    LOG.info(exception);
  }
}

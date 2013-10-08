package org.jetbrains.android.database;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
abstract class AndroidDbErrorReporter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.database.AndroidDbErrorReporter");

  public abstract void reportError(@NotNull String message);

  public void reportError(@NotNull Exception exception) {
    final String exceptionMessage = exception.getMessage();
    final String suffix = exceptionMessage != null && exceptionMessage.length() > 0 ? (": " + exceptionMessage) : "";
    reportError(exception.getClass().getSimpleName() + suffix);
    LOG.debug(exception);
  }
}

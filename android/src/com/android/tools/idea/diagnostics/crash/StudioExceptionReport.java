/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.diagnostics.crash;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.diagnostics.crash.exception.NoPiiException;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import java.util.Arrays;
import java.util.Map;

public class StudioExceptionReport extends BaseStudioReport {
  /**
   * {@link Throwable} classes with messages expected to be useful for debugging and not to contain PII.
   */
  private static final ImmutableList<Class<? extends Throwable>> THROWABLE_CLASSES_TO_TRACK_MESSAGES =
    ImmutableList.of(
      LinkageError.class,
      ReflectiveOperationException.class,
      ArrayIndexOutOfBoundsException.class,
      ClassCastException.class,
      IndexOutOfBoundsException.class,
      NoPiiException.class);

  public static final String KEY_EXCEPTION_INFO = "exception_info";
  private static final String ANDROID_EXCEPTION_TEXT = "com.android.diagnostic.LoggerErrorMessage";

  @NonNull private final String exceptionInfo;
  private final boolean userReported;
  @Nullable private final PluginId pluginId;

  private StudioExceptionReport(@Nullable String version,
                                @NonNull String exceptionInfo,
                                boolean userReported,
                                @Nullable PluginId pluginId,
                                @Nullable Map<String, String> productData) {
    super(version, productData, "Exception");
    this.exceptionInfo = exceptionInfo;
    this.userReported = userReported;
    this.pluginId = pluginId;
  }

  @Override
  protected void serializeTo(@NonNull MultipartEntityBuilder builder) {
    super.serializeTo(builder);

    // Capture kotlin version for kotlin exceptions.
    if (isKotlinOnStack()) {
      builder.addTextBody("kotlinVersion", getKotlinPluginVersionDescription());
    }
    try {
      IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
      if (plugin != null) {
        final String name = plugin.getName();
        final String version = plugin.getVersion();
        builder.addTextBody("plugin.name", name);
        builder.addTextBody("plugin.version", version);
      }
    } catch (Throwable ignored) {
      builder.addTextBody("plugin.name", "exceptionWhileReadingNameVersion");
    }
    builder.addTextBody("userReported", userReported ? "1" : "0");
    builder.addTextBody(KEY_EXCEPTION_INFO, exceptionInfo);
  }

  private boolean isKotlinOnStack() {
    return exceptionInfo.contains("\tat org.jetbrains.kotlin");
  }

  @VisibleForTesting
  @NonNull
  protected String getKotlinPluginVersionDescription() {
    try {
      IdeaPluginDescriptor[] pluginDescriptors = PluginManagerCore.getPlugins();
      return Arrays.stream(pluginDescriptors)
                   .filter(d -> d.getPluginId() != null &&
                                d.getPluginId().getIdString().equals("org.jetbrains.kotlin") &&
                                d.isEnabled())
                   .findFirst()
                   .map(d -> d.getVersion())
                   .orElse("pluginNotLoaded");
    }
    catch (Exception ignored) {
      return "exceptionWhenReadingVersion";
    }
  }

  public static class Builder extends BaseBuilder<StudioExceptionReport, Builder> {
    private String exceptionInfo;
    private boolean userReported;
    private PluginId pluginId;

    @Override
    protected Builder getThis() {
      return this;
    }

    @NonNull
    public Builder setThrowable(@NonNull Throwable throwable, boolean userReported) {
      Throwable cause = getRootCause(throwable);
      this.exceptionInfo = getDescription(cause, userReported);
      this.userReported = userReported;
      this.pluginId = IdeErrorsDialog.findPluginId(cause);
      return this;
    }

    @Override
    public StudioExceptionReport build() {
      return new StudioExceptionReport(getVersion(), exceptionInfo, userReported, pluginId, getProductData());
    }
  }

  // Similar to ExceptionUtil.getRootCause, but attempts to avoid infinite recursion
  @NonNull
  public static Throwable getRootCause(@NonNull Throwable t) {
    int depth = 0;
    while (depth++ < 20) {
      if (t.getCause() == null) return t;
      t = t.getCause();
    }
    return t;
  }

  @NonNull
  public static String getDescription(@NonNull Throwable t) {
    return getDescription(t, false);
  }

  /**
   * Returns an exception description (similar to {@link Throwables#getStackTraceAsString(Throwable)}} with the exception message
   * removed in order to strip off any PII. The exception message is include for some specific exceptions where we know that the
   * message will not have any PII.
   *
   * Exception message is not removed if the exception is reported by the user.
   */
  @NonNull
  public static String getDescription(@NonNull Throwable t, boolean userReported) {
    // Keep the message if the exception is reported by the user.
    // Note: User-provided exceptions in IntelliJ are not proper Throwable objects.
    //   All such exceptions are wrapped in IdeaReportingEvent$TextBasedThrowable, for which
    //   t.getStackTrace() and t.printStackTrace() return different stack traces. t.getStackTrace() gives the
    //   stack of the object creation, t.printStackTrace() - stack of the wrapped exception.
    if (userReported || THROWABLE_CLASSES_TO_TRACK_MESSAGES.stream().anyMatch(c -> c.isInstance(t))) {
      String traceAsString = Throwables.getStackTraceAsString(t);
      return fixStackTraceStringForLoggerErrorMethod(traceAsString);
    }

    // Remove the exception message.

    StackTraceElement[] stackTraceElements = t.getStackTrace();

    // Detect if the exception is from Logger.error. If so, change the type to LoggerErrorMessage
    // and remove the top frame.
    boolean exceptionFromLoggerErrorMethod =
      stackTraceElements != null &&
      stackTraceElements.length >= 1 &&
      Objects.equals(stackTraceElements[0].getMethodName(), "error") &&
      Objects.equals(stackTraceElements[0].getClassName(), Logger.class.getName()) &&
      Objects.equals(t.getClass(), Throwable.class);

    StringBuilder sb = new StringBuilder(256);

    sb.append(exceptionFromLoggerErrorMethod ? ANDROID_EXCEPTION_TEXT : t.getClass().getName());
    sb.append(": <elided>\n"); // note: some message is needed for the backend to parse the report properly

    for (int i = exceptionFromLoggerErrorMethod ? 1 : 0; i < stackTraceElements.length; i++) {
      StackTraceElement el = stackTraceElements[i];
      sb.append("\tat ");
      sb.append(el);
      sb.append('\n');
    }

    return sb.toString();
  }

  /**
   * Logger.error messages show up as java.lang.Throwable exceptions with a top frame always being
   *   Logger.error. This method will detect if the exception string is from such method. If so,
   *   it changes its type to com.android.diagnostic.LoggerErrorMessage and removes the top frame
   *   (Logger.error frame).
   */
  private static String fixStackTraceStringForLoggerErrorMethod(String s) {
    String[] lines = s.split("\n");
    if (lines.length <= 2) {
      return s;
    }
    // Message can be multiline. Find the line with the first frame.
    int indexOfFirstFrame = -1;
    for (int i = 1; i < lines.length; i++) {
      if (lines[i].startsWith("\tat ")) {
        indexOfFirstFrame = i;
        break;
      }
    }
    final String throwableText = Throwable.class.getName();
    final String throwableColonText = throwableText + ":";
    if (indexOfFirstFrame != -1 &&
        (lines[0].equals(throwableText) || lines[0].startsWith(throwableColonText)) &&
        lines[indexOfFirstFrame].startsWith("\tat " + Logger.class.getName() + ".error(")) {
      if (lines[0].equals(throwableText)) {
        lines[0] = ANDROID_EXCEPTION_TEXT;
      } else {
        lines[0] = ANDROID_EXCEPTION_TEXT + lines[0].substring(throwableText.length());
      }
      // Remove Logger.error frame and update exception type
      Stream<String> newLines =
        Stream.concat(Arrays.stream(lines).limit(indexOfFirstFrame), Arrays.stream(lines).skip(indexOfFirstFrame + 1));
      return newLines.collect(Collectors.joining("\n"));
    }
    return s;
  }
}

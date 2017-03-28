/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.HashMap;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.diagnostics.crash.GoogleCrash.KEY_EXCEPTION_INFO;

public abstract class CrashReport {
  public static final String PRODUCT_ANDROID_STUDIO = "AndroidStudio"; // must stay in sync with backend registration

  /** {@link Throwable} classes with messages expected to be useful for debugging and not to contain PII. */
  private static final ImmutableSet<Class<? extends Throwable>> THROWABLE_CLASSES_TO_TRACK_MESSAGES = ImmutableSet.of(
    ArrayIndexOutOfBoundsException.class,
    ClassCastException.class,
    ClassNotFoundException.class,
    IndexOutOfBoundsException.class
  );

  public enum Type {
    Crash,
    Exception,
    Performance,
  }

  @NotNull public final String productId;
  @Nullable public final String version;
  @Nullable public final Map<String, String> productData;
  @NotNull private final Type myType;

  private CrashReport(@NotNull String productId, @Nullable String version, @Nullable Map<String, String> productData, @NotNull Type type) {
    this.productId = productId;
    this.version = version;
    this.productData = productData;
    myType = type;
  }

  public void serialize(@NotNull MultipartEntityBuilder builder) {
    builder.addTextBody("type", myType.toString());

    if (productData != null) {
      productData.forEach(builder::addTextBody);
    }

    serializeTo(builder);
  }

  protected abstract void serializeTo(@NotNull MultipartEntityBuilder builder);

  private static class ExceptionReport extends CrashReport {
    @NotNull private final String myExceptionInfo;

    private ExceptionReport(@NotNull String productId,
                            @Nullable String version,
                            @NotNull String exceptionInfo,
                            @Nullable Map<String, String> productData) {
      super(productId, version, productData, Type.Exception);
      myExceptionInfo = exceptionInfo;
    }

    @Override
    protected void serializeTo(@NotNull MultipartEntityBuilder builder) {
      builder.addTextBody(KEY_EXCEPTION_INFO, myExceptionInfo);
    }
  }

  private static class StudioCrashReport extends CrashReport {
    private final List<String> myDescriptions;

    private StudioCrashReport(@NotNull String productId,
                              @Nullable String version,
                              @NotNull List<String> descriptions,
                              @Nullable Map<String, String> productData) {
      super(productId, version, productData, Type.Crash);
      myDescriptions = descriptions;
    }

    @Override
    protected void serializeTo(@NotNull MultipartEntityBuilder builder) {
      builder.addTextBody("numCrashes", Integer.toString(myDescriptions.size()));
      builder.addTextBody("crashDesc", Joiner.on("\n\n").join(myDescriptions));
    }
  }

  private static class StudioPerformanceWatcherReport extends CrashReport {
    private final String myFileName;
    private final String myThreadDump;

    private StudioPerformanceWatcherReport(@NotNull String productId,
                                           @Nullable String version,
                                           @NotNull String fileName,
                                           @NotNull String threadDump,
                                           @Nullable Map<String, String> productData) {
      super(productId, version, productData, Type.Performance);
      myFileName = fileName;
      myThreadDump = threadDump;
    }

    @Override
    protected void serializeTo(@NotNull MultipartEntityBuilder builder) {
      //String edtStack = ThreadDumper.getEdtStackForCrash(myThreadDump);
      //if (edtStack != null) {
      //  builder.addTextBody(KEY_EXCEPTION_INFO, edtStack);
      //}

      builder.addTextBody(myFileName,
                          myThreadDump,
                          ContentType.create("text/plain", Charsets.UTF_8));
    }
  }

  public static class Builder {
    private String myProductId = PRODUCT_ANDROID_STUDIO;
    private String myVersion;
    private Type myType = Type.Exception;
    private String myExceptionInfo = "<unknown>";
    private List<String> myCrashDescriptions;
    private String myThreadDump;
    private String myFileName;
    private Map<String,String> myProductData;

    private Builder() {
    }

    @NotNull
    public Builder setProduct(@NotNull String productId) {
      myProductId = productId;
      return this;
    }

    @NotNull
    public Builder setVersion(@NotNull String version) {
      myVersion = version;
      return this;
    }

    @NotNull
    public Builder addProductData(@NotNull Map<String,String> kv) {
      if (myProductData == null) {
        myProductData = new HashMap<>();
      }

      myProductData.putAll(kv);
      return this;
    }

    @NotNull
    private Builder setType(@NotNull Type type) {
      myType = type;
      return this;
    }

    @NotNull
    private Builder setThrowable(@NotNull Throwable t) {
      //noinspection ThrowableResultOfMethodCallIgnored
      myExceptionInfo = getDescription(getRootCause(t));
      return this;
    }

    @NotNull
    private Builder setDescriptions(@NotNull List<String> descriptions) {
      myCrashDescriptions = descriptions;
      return this;
    }

    @NotNull
    private Builder setThreadDump(@NotNull String fileName, @NotNull String threadDump) {
      myFileName = fileName;
      myThreadDump = threadDump;
      return this;
    }

    @NotNull
    public CrashReport build() {
      switch (myType) {
        case Crash:
          return new StudioCrashReport(myProductId, myVersion, myCrashDescriptions, myProductData);
        case Performance:
          return new StudioPerformanceWatcherReport(myProductId, myVersion, myFileName, myThreadDump, myProductData);
        default:
        case Exception:
          return new ExceptionReport(myProductId, myVersion, myExceptionInfo, myProductData);
      }
    }

    @NotNull
    public static Builder createForException(@NotNull Throwable t) {
      return new Builder()
        .setType(Type.Exception)
        .setThrowable(t);
    }

    @NotNull
    public static Builder createForCrashes(@NotNull List<String> descriptions) {
      return new Builder()
        .setType(Type.Crash)
        .setDescriptions(descriptions);
    }

    @NotNull
    public static Builder createForPerfReport(@NotNull String fileName, @NotNull String threadDump) {
      return new Builder()
        .setType(Type.Performance)
        .setThreadDump(fileName, threadDump);
    }
  }

  // Similar to ExceptionUntil.getRootCause, but attempts to avoid infinite recursion
  @NotNull
  public static Throwable getRootCause(@NotNull Throwable t) {
    int depth = 0;
    while (depth++ < 20) {
      if (t.getCause() == null) return t;
      t = t.getCause();
    }
    return t;
  }

  /**
   * Returns an exception description (similar to {@link ExceptionUtil#getThrowableText(Throwable)} with the exception message
   * removed in order to strip off any PII. The exception message is include for some specific exceptions where we know that the
   * message will not have any PII.
   */
  @NotNull
  public static String getDescription(@NotNull Throwable t) {
    if (THROWABLE_CLASSES_TO_TRACK_MESSAGES.contains(t.getClass())) {
      return ExceptionUtil.getThrowableText(t);
    }

    StringBuilder sb = new StringBuilder(256);

    sb.append(t.getClass().getName());
    sb.append(": <elided>\n"); // note: some message is needed for the backend to parse the report properly

    for (StackTraceElement el : t.getStackTrace()) {
      sb.append("\tat ");
      sb.append(el);
      sb.append('\n');
    }

    return sb.toString();
  }
}

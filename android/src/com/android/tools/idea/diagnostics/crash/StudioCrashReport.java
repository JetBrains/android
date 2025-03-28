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

import com.android.tools.analytics.crash.GoogleCrashReporter;
import com.android.tools.idea.diagnostics.crash.exception.JvmCrashException;
import com.android.tools.idea.diagnostics.crash.exception.NonGracefulExitException;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StudioCrashReport extends BaseStudioReport {
  private final List<String> descriptions;
  private final boolean isJvmCrash;
  private final long uptimeInMs;
  private final String errorSignal;
  private final String errorFrame;
  private final String errorThread;
  private final String nativeStack;

  private StudioCrashReport(@Nullable String version,
                            @NotNull List<String> descriptions,
                            @Nullable Map<String, String> productData,
                            boolean isJvmCrash,
                            long uptimeInMs,
                            @NotNull String errorSignal,
                            @NotNull String errorFrame,
                            @NotNull String errorThread,
                            @NotNull String nativeStack) {
    super(version, productData, "Crash");
    this.descriptions = descriptions;
    this.isJvmCrash = isJvmCrash;
    this.uptimeInMs = uptimeInMs;
    this.errorSignal = errorSignal;
    this.errorFrame = errorFrame;
    this.errorThread = errorThread;
    this.nativeStack = nativeStack;
  }

  @Override
  protected void serializeTo(@NotNull MultipartEntityBuilder builder) {
    super.serializeTo(builder);

    builder.addTextBody("numCrashes", Integer.toString(descriptions.size()));
    builder.addTextBody("crashDesc", Joiner.on("\n\n").join(descriptions));

    Exception exception = isJvmCrash ? new JvmCrashException() : new NonGracefulExitException();

    // Cut stacktrace to necessary minimum (only first two lines: Exception name and first stack frame)
    String[] exceptionInfoAllLines = Throwables.getStackTraceAsString(exception).split("[\\r\\n]+");
    String exceptionInfo = Arrays.stream(exceptionInfoAllLines).limit(2).collect(Collectors.joining("\n"));
    GoogleCrashReporter.addBodyToBuilder(builder, StudioExceptionReport.KEY_EXCEPTION_INFO, exceptionInfo);

    if (isJvmCrash) {
      GoogleCrashReporter.addBodyToBuilder(builder, "errorSignal", errorSignal);
      GoogleCrashReporter.addBodyToBuilder(builder, "errorFrame", errorFrame);
      GoogleCrashReporter.addBodyToBuilder(builder, "errorThread", errorThread);
      GoogleCrashReporter.addBodyToBuilder(builder, "nativeStack", nativeStack);
    }
  }

  @Override
  protected void overrideDefaultParameters(Map<String, String> parameters) {
    if (uptimeInMs >= 0) {
      parameters.put("ptime", Long.toString(uptimeInMs));
    }
  }

  public static class Builder extends BaseBuilder<StudioCrashReport, Builder> {
    private List<String> descriptions;
    private boolean isJvmCrash = false;
    private long uptimeInMs = -1;
    private String errorSignal = "";
    private String errorFrame = "";
    private String errorThread = "";
    private String nativeStack = "";

    @Override
    protected Builder getThis() {
      return this;
    }

    @NotNull
    public Builder setDescriptions(@NotNull List<String> descriptions) {
      this.descriptions = descriptions;
      return this;
    }

    @NotNull
    public Builder setIsJvmCrash(boolean isJvmCrash) {
      this.isJvmCrash = isJvmCrash;
      return this;
    }

    @NotNull
    public Builder setUptimeInMs(long uptimeInMs) {
      this.uptimeInMs = uptimeInMs;
      return this;
    }

    @NotNull
    public Builder setErrorSignal(String errorSignal) {
      this.errorSignal = errorSignal;
      return this;
    }

    @NotNull
    public Builder setErrorFrame(String errorFrame) {
      this.errorFrame = errorFrame;
      return this;
    }

    @NotNull
    public Builder setErrorThread(String errorThread) {
      this.errorThread = errorThread;
      return this;
    }

    @NotNull
    public Builder setNativeStack(String nativeStack) {
      this.nativeStack = nativeStack;
      return this;
    }

    @Override
    public StudioCrashReport build() {
      return new StudioCrashReport(getVersion(), descriptions, getProductData(), isJvmCrash, uptimeInMs, errorSignal, errorFrame,
                                   errorThread, nativeStack);
    }
  }
}
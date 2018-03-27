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
import com.android.tools.idea.diagnostics.crash.exception.JvmCrashException;
import com.android.tools.idea.diagnostics.crash.exception.NonGracefulExitException;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StudioCrashReport extends BaseStudioReport {
  private final List<String> descriptions;
  private final boolean isJvmCrash;
  private final long uptimeInMs;

  private StudioCrashReport(@Nullable String version,
                            @NonNull List<String> descriptions,
                            @Nullable Map<String, String> productData,
                            boolean isJvmCrash,
                            long uptimeInMs) {
    super(version, productData, "Crash");
    this.descriptions = descriptions;
    this.isJvmCrash = isJvmCrash;
    this.uptimeInMs = uptimeInMs;
  }

  @Override
  protected void serializeTo(@NonNull MultipartEntityBuilder builder) {
    builder.addTextBody("numCrashes", Integer.toString(descriptions.size()));
    builder.addTextBody("crashDesc", Joiner.on("\n\n").join(descriptions));

    Exception exception = isJvmCrash ? new JvmCrashException() : new NonGracefulExitException();

    // Cut stacktrace to necessary minimum (only first two lines: Exception name and first stack frame)
    String[] exceptionInfoAllLines = Throwables.getStackTraceAsString(exception).split("[\\r\\n]+");
    String exceptionInfo = Arrays.stream(exceptionInfoAllLines).limit(2).collect(Collectors.joining("\n"));
    builder.addTextBody(StudioExceptionReport.KEY_EXCEPTION_INFO, exceptionInfo);
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

    @Override
    protected Builder getThis() {
      return this;
    }

    @NonNull
    public Builder setDescriptions(@NonNull List<String> descriptions) {
      this.descriptions = descriptions;
      return this;
    }

    @NonNull
    public Builder setIsJvmCrash(boolean isJvmCrash) {
      this.isJvmCrash = isJvmCrash;
      return this;
    }

    @NonNull
    public Builder setUptimeInMs(long uptimeInMs) {
      this.uptimeInMs = uptimeInMs;
      return this;
    }

    @Override
    public StudioCrashReport build() {
      return new StudioCrashReport(getVersion(), descriptions, getProductData(), isJvmCrash, uptimeInMs);
    }
  }
}
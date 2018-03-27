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
import com.google.common.base.Charsets;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import java.util.Map;

public class StudioPerformanceWatcherReport extends BaseStudioReport {
  @NonNull private final String fileName;
  @NonNull private final String threadDump;

  private StudioPerformanceWatcherReport(@Nullable String version,
                                         @NonNull String fileName,
                                         @NonNull String threadDump,
                                         @Nullable Map<String, String> productData) {
    super(version, productData, "Performance");
    this.fileName = fileName;
    this.threadDump = threadDump;
  }

  @Override
  protected void serializeTo(@NonNull MultipartEntityBuilder builder) {
    builder.addTextBody(fileName, threadDump, ContentType.create("text/plain", Charsets.UTF_8));
  }

  public static final class Builder extends BaseBuilder<StudioPerformanceWatcherReport, Builder> {
    private String fileName;
    private String threadDump;

    @Override
    protected Builder getThis() {
      return this;
    }

    @NonNull
    public Builder setFile(@NonNull String fileName) {
      this.fileName = fileName;
      return this;
    }

    @NonNull
    public Builder setThreadDump(@NonNull String threadDump) {
      this.threadDump = threadDump;
      return this;
    }

    @Override
    public StudioPerformanceWatcherReport build() {
      return new StudioPerformanceWatcherReport(getVersion(), fileName, threadDump, getProductData());
    }
  }
}
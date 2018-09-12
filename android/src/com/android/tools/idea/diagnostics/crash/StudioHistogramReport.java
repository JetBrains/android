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
import com.intellij.diagnostic.ThreadDumper;
import java.util.Map;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

public class StudioHistogramReport extends BaseStudioReport {
  private static final String EXCEPTION_TYPE = "com.android.OutOfMemory";

  private static final String EMPTY_OOM_STACKTRACE =
    EXCEPTION_TYPE + ": \n" +
    "\tat " + StudioHistogramReport.class.getName() + ".missingEdtStack(Unknown source)";

  private @NonNull String threadDump;
  private @NonNull String histogram;

  public StudioHistogramReport(@Nullable String version,
                        @NonNull String threadDump,
                        @NonNull String histogram,
                        @Nullable Map<String, String> productData) {
    super(version, productData, "Histogram");
    this.threadDump = threadDump;
    this.histogram = histogram;
  }

  @Override
  protected void serializeTo(@NonNull MultipartEntityBuilder builder) {
    super.serializeTo(builder);

    String edtStack = ThreadDumper.getEdtStackForCrash(threadDump, EXCEPTION_TYPE);

    builder.addTextBody(StudioExceptionReport.KEY_EXCEPTION_INFO,
                        edtStack != null ? edtStack : EMPTY_OOM_STACKTRACE);
    builder.addTextBody("histogram", histogram, ContentType.create("text/plain", Charsets.UTF_8));
    builder.addTextBody("threadDump", threadDump, ContentType.create("text/plain", Charsets.UTF_8));
  }

  public static final class Builder extends BaseBuilder<StudioHistogramReport, StudioHistogramReport.Builder> {
    private String histogram;
    private String threadDump;

    @Override
    protected StudioHistogramReport.Builder getThis() {
      return this;
    }

    @NonNull
    public StudioHistogramReport.Builder setHistogram(@NonNull String histogram) {
      this.histogram = histogram;
      return this;
    }

    @NonNull
    public StudioHistogramReport.Builder setThreadDump(@NonNull String threadDump) {
      this.threadDump = threadDump;
      return this;
    }

    @Override
    public StudioHistogramReport build() {
      return new StudioHistogramReport(getVersion(), threadDump, histogram, getProductData());
    }
  }

}

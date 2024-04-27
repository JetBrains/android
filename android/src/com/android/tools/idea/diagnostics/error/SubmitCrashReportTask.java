/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.diagnostics.error;

import com.android.tools.analytics.crash.CrashReport;
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport;
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SubmitCrashReportTask extends Task.Backgroundable {
  private final Consumer<String> myCallback;
  private final Consumer<Exception> myErrorCallback;
  private final Throwable myThrowable;
  private final Map<String, String> myParams;

  public SubmitCrashReportTask(@Nullable Project project,
                               @NotNull String title,
                               boolean canBeCancelled,
                               @Nullable Throwable throwable,
                               @NotNull Map<String, String> params,
                               @NotNull final Consumer<String> callback,
                               @NotNull final Consumer<Exception> errorCallback) {
    super(project, title, canBeCancelled);

    myThrowable = throwable;
    myParams = params;
    myCallback = callback;
    myErrorCallback = errorCallback;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);

    CrashReport report =
      new StudioExceptionReport.Builder()
        .setThrowable(myThrowable, true, false) // Logs have already been captured in productData
        .addProductData(getProductData())
        .build();
    CompletableFuture<String> future = StudioCrashReporter.getInstance().submit(report, true);

    try {
      String token = future.get(20, TimeUnit.SECONDS); // arbitrary limit, we don't really want an error report task to take longer
      myCallback.consume(token);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      myErrorCallback.consume(e);
    }
  }

  @NotNull
  private Map<String, String> getProductData() {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();

    myParams.forEach((k, v) -> {
      builder.put(k, StringUtil.notNullize(v));
    });

    return builder.build();
  }
}

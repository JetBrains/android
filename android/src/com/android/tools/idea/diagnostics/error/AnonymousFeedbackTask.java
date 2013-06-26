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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;

import static com.android.tools.idea.diagnostics.error.AnonymousFeedback.sendFeedback;

public class AnonymousFeedbackTask extends Task.Backgroundable {
  private final Consumer<String> myCallback;
  private final Consumer<Exception> myErrorCallback;
  private final Throwable myThrowable;
  private final Map<String, String> myParams;
  private final String myErrorMessage;
  private final String myErrorDescription;
  private final String myAppVersion;

  public AnonymousFeedbackTask(@Nullable Project project,
                               @NotNull String title,
                               boolean canBeCancelled,
                               @Nullable Throwable throwable,
                               Map<String, String> params,
                               String errorMessage,
                               String errorDescription,
                               String appVersion,
                               final Consumer<String> callback,
                               final Consumer<Exception> errorCallback) {
    super(project, title, canBeCancelled);

    myThrowable = throwable;
    myParams = params;
    myErrorMessage = errorMessage;
    myErrorDescription = errorDescription;
    myAppVersion = appVersion;
    myCallback = callback;
    myErrorCallback = errorCallback;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    try {
      String token = sendFeedback(new ProxyHttpConnectionFactory(), myThrowable, myParams,
                                  myErrorMessage, myErrorDescription, myAppVersion);
      myCallback.consume(token);
    }
    catch (Exception e) {
      myErrorCallback.consume(e);
    }
  }

  private static class ProxyHttpConnectionFactory extends AnonymousFeedback.HttpConnectionFactory {
    @Override
    protected HttpURLConnection openHttpConnection(String path) throws IOException {
      return HttpConfigurable.getInstance().openHttpConnection(path);
    }
  }
}

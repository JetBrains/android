/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.notification.NotificationListener;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class LaunchResult {
  private boolean mySuccess;
  private String myError;
  private String myErrorId;
  private String myConsoleError;
  private NotificationListener myNotificationListener;

  // Hyperlink to be appended to the footer of the console error message.
  private String myConsoleHyperlinkText;
  private HyperlinkInfo myConsoleHyperlinkInfo;
  private List<Runnable> myOnFinishedCallbacks;

  public LaunchResult() {
    mySuccess = true;
    myError = "";
    myErrorId = "";
    myConsoleError = "";
    myConsoleHyperlinkText = "";
    myConsoleHyperlinkInfo = null;
    myNotificationListener = null;
    myOnFinishedCallbacks = new ArrayList<>();
  }

  public void setSuccess(boolean success) {
    mySuccess = success;
  }

  public boolean getSuccess() {
    return mySuccess;
  }

  public void setError(String error) {
    myError = error;
  }

  public String getError() {
    return myError;
  }

  public void setErrorId(String id) {
    myErrorId = id;
  }

  public String getErrorId() {
    return myErrorId;
  }

  public void setConsoleError(String error) {
    myConsoleError = error;
  }

  public String getConsoleError() {
    return myConsoleError;
  }

  public void setConsoleHyperlink(String hyperlinkText, HyperlinkInfo hyperlinkInfo) {
    myConsoleHyperlinkText = hyperlinkText;
    myConsoleHyperlinkInfo = hyperlinkInfo;
  }

  public String getConsoleHyperlinkText() {
    return myConsoleHyperlinkText;
  }

  public HyperlinkInfo getConsoleHyperlinkInfo() {
    return myConsoleHyperlinkInfo;
  }

  public NotificationListener getNotificationListener() {
    return myNotificationListener;
  }

  public void setNotificationListener(NotificationListener listener) {
    myNotificationListener = listener;
  }

  public List<Runnable> onFinishedCallbacks() {
    return myOnFinishedCallbacks;
  }

  public void addOnFinishedCallback(Runnable runnable) {
    myOnFinishedCallbacks.add(runnable);
  }

  @NotNull
  public static LaunchResult success() {
    return new LaunchResult();
  }

  @NotNull
  public static LaunchResult error(@NotNull String errorId, @NotNull String taskDescription) {
    LaunchResult result = new LaunchResult();
    result.setSuccess(false);
    result.setErrorId(errorId);
    result.setError("Error " + taskDescription);
    result.setConsoleError("Error while " + taskDescription);
    return result;
  }
}

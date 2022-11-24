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

import static com.android.tools.idea.run.tasks.LaunchResult.Result.ERROR;
import static com.android.tools.idea.run.tasks.LaunchResult.Result.SUCCESS;
import static com.android.tools.idea.run.tasks.LaunchResult.Result.WARNING;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class LaunchResult {
  public enum Result {
    SUCCESS,
    WARNING,
    ERROR,
  }
  private Result myResult;
  private String myMessage;
  private String myErrorId;
  private String myConsoleMessage;

  private final List<Runnable> myOnFinishedCallbacks;

  public LaunchResult() {
    myResult = SUCCESS;
    myMessage = "";
    myErrorId = "";
    myConsoleMessage = "";
    myOnFinishedCallbacks = new ArrayList<>();
  }

  public void setResult(Result result) {
    myResult = result;
  }

  public Result getResult() {
    return myResult;
  }

  public void setMessage(String message) {
    myMessage = message;
  }

  public String getMessage() {
    return myMessage;
  }

  public void setErrorId(String id) {
    myErrorId = id;
  }

  public String getErrorId() {
    return myErrorId;
  }

  public void setConsoleMessage(String consoleMessage) {
    myConsoleMessage = consoleMessage;
  }

  public String getConsoleMessage() {
    return myConsoleMessage;
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
    result.setResult(ERROR);
    result.setErrorId(errorId);
    result.setMessage("Error " + taskDescription);
    result.setConsoleMessage("Error while " + taskDescription);
    return result;
  }

  @NotNull
  public static LaunchResult warning(@NotNull String message) {
    LaunchResult result = new LaunchResult();
    result.setResult(WARNING);
    result.setMessage(message);
    result.setConsoleMessage(message);
    return result;
  }
}

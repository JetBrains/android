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
package com.android.tools.idea.gradle.project.build.invoker;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.progress.ProgressIndicator;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BuildStopper {
  @NotNull private final Map<ExternalSystemTaskId, CancellationTokenSource> myMap = new ConcurrentHashMap<>();

  @NotNull
  public CancellationTokenSource createAndRegisterTokenSource(@NotNull ExternalSystemTaskId id) {
    CancellationTokenSource tokenSource = GradleConnector.newCancellationTokenSource();
    myMap.put(id, tokenSource);
    return tokenSource;
  }

  @TestOnly
  void register(@NotNull ExternalSystemTaskId id, CancellationTokenSource tokenSource) {
    myMap.put(id, tokenSource);
  }

  public void attemptToStopBuild(@NotNull ExternalSystemTaskId id, @Nullable ProgressIndicator progressIndicator) {
    if (progressIndicator != null) {
      if (progressIndicator.isCanceled()) {
        return;
      }
      if (progressIndicator.isRunning()) {
        progressIndicator.setText("Stopping Gradle build...");
        progressIndicator.cancel();
      }
    }
    CancellationTokenSource token = remove(id);
    if (token != null) {
      token.cancel();
    }
  }

  @Nullable
  public CancellationTokenSource remove(@NotNull ExternalSystemTaskId taskId) {
    return myMap.remove(taskId);
  }

  public boolean contains(@NotNull ExternalSystemTaskId taskId) {
    return myMap.containsKey(taskId);
  }

  @TestOnly
  @Nullable
  CancellationTokenSource get(@NotNull ExternalSystemTaskId taskId) {
    return myMap.get(taskId);
  }
}

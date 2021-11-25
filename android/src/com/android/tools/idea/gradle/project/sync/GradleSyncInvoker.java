/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.IN_BACKGROUND_ASYNC;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.MODAL_SYNC;
import static java.util.Collections.emptyList;

import com.android.annotations.concurrency.WorkerThread;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public interface GradleSyncInvoker {
  void requestProjectSync(@NotNull Project project,
                          @NotNull GradleSyncStats.Trigger trigger);

  void requestProjectSync(@NotNull Project project,
                          @NotNull GradleSyncStats.Trigger trigger,
                          @Nullable GradleSyncListener listener);

  void requestProjectSync(@NotNull Project project, @NotNull Request request);

  void requestProjectSync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener);

  @WorkerThread
  void fetchAndMergeNativeVariants(@NotNull Project project,
                                   @NotNull Set<@NotNull String> requestedAbis);

  @WorkerThread
  @NotNull List<GradleModuleModels> fetchGradleModels(@NotNull Project project);

  @NotNull
  static GradleSyncInvoker getInstance() {
    return ApplicationManager.getApplication().getService(GradleSyncInvoker.class);
  }

  class Request {
    public final GradleSyncStats.Trigger trigger;

    public boolean runInBackground = true;
    public boolean skipPreSyncChecks;

    // Perform a variant-only sync if not null.

    @VisibleForTesting
    @NotNull
    public static Request testRequest() {
      return new Request(TRIGGER_TEST_REQUESTED);
    }

    public Request(@NotNull GradleSyncStats.Trigger trigger) {
      this.trigger = trigger;
    }

    @NotNull
    public ProgressExecutionMode getProgressExecutionMode() {
      return runInBackground ? IN_BACKGROUND_ASYNC : MODAL_SYNC;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request request = (Request)o;
      return trigger == request.trigger &&
             runInBackground == request.runInBackground &&
             skipPreSyncChecks == request.skipPreSyncChecks;
    }

    @Override
    public int hashCode() {
      return Objects
        .hash(trigger, runInBackground, skipPreSyncChecks);
    }

    @Override
    public String toString() {
      return "RequestSettings{" +
             "trigger=" + trigger +
             ", runInBackground=" + runInBackground +
             ", skipPreSyncChecks=" + skipPreSyncChecks +
             '}';
    }
  }

  @TestOnly
  class FakeInvoker implements GradleSyncInvoker {
    @Override
    public void requestProjectSync(@NotNull Project project,
                                   GradleSyncStats.@NotNull Trigger trigger) {

    }

    @Override
    public void requestProjectSync(@NotNull Project project,
                                   GradleSyncStats.@NotNull Trigger trigger,
                                   @Nullable GradleSyncListener listener) {

      if (listener != null) {
        listener.syncSkipped(project);
      }
    }

    @Override
    public void requestProjectSync(@NotNull Project project,
                                   @NotNull Request request) {

    }

    @Override
    public void requestProjectSync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
      if (listener != null) {
        listener.syncSkipped(project);
      }
    }

    @Override
    public void fetchAndMergeNativeVariants(@NotNull Project project,
                                            @NotNull Set<@NotNull String> requestedAbis) {

    }

    @Override
    public @NotNull List<GradleModuleModels> fetchGradleModels(@NotNull Project project) {
      return emptyList();
    }
  }
}

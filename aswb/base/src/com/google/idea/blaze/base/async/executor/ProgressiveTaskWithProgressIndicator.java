/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.async.executor;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/**
 * Helper class for running a task with a progress dialog, by default using {@link BlazeExecutor}.
 */
public class ProgressiveTaskWithProgressIndicator {

  /** The type of modality used to launch tasks */
  public enum Modality {
    MODAL, // This task must start in the foreground and stay there.
    BACKGROUNDABLE, // This task will start in the foreground, but can be sent to the background.
    ALWAYS_BACKGROUND // This task will start in the background and stay there.
  }

  @Nullable private final Project project;
  private final String title;
  private boolean cancelable = true;
  private Modality modality = Modality.ALWAYS_BACKGROUND;
  private ListeningExecutorService executor = BlazeExecutor.getInstance().getExecutor();

  private ProgressiveTaskWithProgressIndicator(@Nullable Project project, String title) {
    this.project = project;
    this.title = title;
  }

  public static ProgressiveTaskWithProgressIndicator builder(
      @Nullable Project project, String title) {
    return new ProgressiveTaskWithProgressIndicator(project, title);
  }

  @CanIgnoreReturnValue
  public ProgressiveTaskWithProgressIndicator setCancelable(boolean cancelable) {
    this.cancelable = cancelable;
    return this;
  }

  @CanIgnoreReturnValue
  public ProgressiveTaskWithProgressIndicator setModality(Modality modality) {
    this.modality = modality;
    return this;
  }

  @CanIgnoreReturnValue
  public ProgressiveTaskWithProgressIndicator setExecutor(ListeningExecutorService executor) {
    this.executor = executor;
    return this;
  }

  public ListenableFuture<Void> submitTask(Progressive progressive) {
    return submitTaskWithResult(
        indicator -> {
          progressive.run(indicator);
          return null;
        });
  }

  public void submitTaskLater(Progressive progressive) {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              @SuppressWarnings("unused") // go/futurereturn-lsc
              Future<?> possiblyIgnoredError =
                  submitTaskWithResult(
                      indicator -> {
                        progressive.run(indicator);
                        return null;
                      });
            });
  }

  /**
   * Runs the given task on the specified executor (defaulting to BlazeExecutor's executor) with a
   * progress dialog.
   */
  public <T> ListenableFuture<T> submitTaskWithResult(ProgressiveWithResult<T> progressive) {
    // The progress indicator must be created on the UI thread.
    final ProgressWindow indicator =
        UIUtil.invokeAndWaitIfNeeded(
            () -> {
              if (modality == Modality.MODAL) {
                ProgressWindow window = new ProgressWindow(cancelable, project);
                window.setTitle(title);
                return window;
              } else {
                PerformInBackgroundOption backgroundOption =
                    modality == Modality.BACKGROUNDABLE
                        ? PerformInBackgroundOption.DEAF
                        : PerformInBackgroundOption.ALWAYS_BACKGROUND;
                return new BackgroundableProcessIndicator(
                    project, title, backgroundOption, "Cancel", "Cancel", cancelable);
              }
            });

    indicator.setIndeterminate(true);
    indicator.start();
    final ListenableFuture<T> future =
        executor.submit(
            () ->
                ProgressManager.getInstance()
                    .runProcess(() -> progressive.compute(indicator), indicator));
    if (cancelable) {
      indicator.addStateDelegate(
          new AbstractProgressIndicatorExBase() {
            @Override
            public void cancel() {
              super.cancel();
              future.cancel(true);
            }
          });
    }
    future.addListener(
        () -> {
          if (indicator.isRunning()) {
            indicator.stop();
            indicator.processFinish();
          }
        },
        MoreExecutors.directExecutor());
    return future;
  }
}

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
package com.android.tools.idea.sdk.progress;

import com.android.annotations.VisibleForTesting;
import com.android.repository.api.ProgressRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;

/**
 * {@link ProgressRunner} implementation that uses Studio's {@link ProgressManager} mechanism for showing progress and running tasks.
 * Invokes all tasks on the UI thread.
 */
public class StudioProgressRunner implements ProgressRunner {
  private boolean myModal;
  private final boolean myCancellable;
  private final Project myProject;
  private final String myProgressTitle;

  public StudioProgressRunner(boolean modal,
                              boolean cancellable,
                              String progressTitle,
                              @Nullable Project project) {
    myModal = modal;
    myCancellable = cancellable;
    myProject = project;
    myProgressTitle = progressTitle;
  }

  @Override
  public void runAsyncWithProgress(@NotNull final ProgressRunnable r) {
    runAsyncWithProgress(r, false);
  }

  @VisibleForTesting
  public void runAsyncWithProgress(@NotNull final ProgressRunnable r, boolean overrideTestMode) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        Task.Backgroundable task = new Task.Backgroundable(myProject, myProgressTitle, myCancellable, new PerformInBackgroundOption() {
          @Override
          public boolean shouldStartInBackground() {
            return !myModal;
          }
        }) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            ApplicationManager.getApplication().executeOnPooledThread(
              () -> r.run(new RepoProgressIndicatorAdapter(indicator), StudioProgressRunner.this));
          }

          @Override
          public boolean isConditionalModal() {
            return true;
          }

          @Override
          public boolean isHeadless() {
            return overrideTestMode ? false : super.isHeadless();
          }
        };

        boolean hasOpenProjects = ProjectManager.getInstance().getOpenProjects().length > 0;
        if (hasOpenProjects) {
          ProgressManager.getInstance().run(task);
        }
        else {
          // If we don't have any open projects run(task) will show a modal popup no matter what.
          // Instead explicitly use an empty progress indicator to suppress that.
          ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new EmptyProgressIndicator());
        }
      }
    });
  }

  @Override
  public void runSyncWithProgress(@NotNull final ProgressRunnable progressRunnable) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      Task task = new Task.Modal(myProject, myProgressTitle, myCancellable) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          doRunSync(indicator, progressRunnable);
        }
      };
      ProgressManager.getInstance().run(task);
    });
  }

  private void doRunSync(@NotNull ProgressIndicator indicator, @NotNull ProgressRunnable progressRunnable) {
    RepoProgressIndicatorAdapter repoProgress = new RepoProgressIndicatorAdapter(indicator);
    try {
      ApplicationManager.getApplication().executeOnPooledThread(() -> progressRunnable.run(repoProgress, this)).get();
    }
    catch (InterruptedException | ExecutionException e) {
      // shouldn't happen
    }
  }

  @Override
  public void runSyncWithoutProgress(@NotNull Runnable r) {
    r.run();
  }
}

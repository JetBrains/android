/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DeviceMonitorStatus {
  private Project myProject;
  @Nullable private SamplerBackgroundTask myTask;
  @NotNull private Set<BaseMonitorView> myViews = new HashSet<BaseMonitorView>();

  public DeviceMonitorStatus(@NotNull Project project) {
    myProject = project;
  }

  public void registerView(@NotNull BaseMonitorView view) {
    assert (myProject != null);
    if (myTask != null) {
      myTask.exit();
      myTask = null;
    }
    myViews.add(view);
    view.getSampler().addListener(new TimelineEventListener() {
      @Override
      public void onStart() {
        statusChanged();
      }

      @Override
      public void onStop() {
        statusChanged();
      }

      @Override
      public void onEvent(@NotNull TimelineEvent event) {
      }
    });
  }

  public void statusChanged() {
    boolean showing = false;
    for (BaseMonitorView view : myViews) {
      showing = view.isShowing() || showing;
    }
    if (showing && myTask != null) {
      myTask.exit();
      myTask = null;
    }
    else if (!showing && myTask == null) {
      myTask = new SamplerBackgroundTask(myProject, myViews);
      ProgressManager.getInstance().run(myTask);
    }
  }

  private static class SamplerBackgroundTask extends Task.Backgroundable {
    @NotNull private final Set<BaseMonitorView> myViews;
    @NotNull private final CountDownLatch myLatch;

    public SamplerBackgroundTask(@NotNull Project project, @NotNull Set<BaseMonitorView> views) {
      super(project, "Monitoring device...", true);
      myViews = views;
      myLatch = new CountDownLatch(1);
    }

    public void exit() {
      myLatch.countDown();
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);

      while (myLatch.getCount() > 0) {
        try {
          myLatch.await(200, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
          exit();
          break;
        }

        if (indicator.isCanceled()) {
          for (BaseMonitorView view : myViews) {
            view.getSampler().stop();
          }
          exit();
        }
      }
    }
  }
}

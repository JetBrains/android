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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DeviceSamplerView {
  private Project myProject;
  @Nullable private SamplerBackgroundTask myTask;
  @NotNull private Set<BaseMonitorView> myViews = new HashSet<BaseMonitorView>();
  public DeviceSamplerView(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  private static List<BaseMonitorView> getRunningViews(@NotNull Set<BaseMonitorView> allViews) {
    // Find all views that are not showing and have a sampler running.
    List<BaseMonitorView> runningViews = new ArrayList<BaseMonitorView>();
    for (BaseMonitorView viewInstance : allViews) {
      if (viewInstance.getSampler().isRunning()) {
        runningViews.add(viewInstance);
      }
    }
    return runningViews;
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
        notifySamplerViewStateChange();
      }

      @Override
      public void onStop() {
        notifySamplerViewStateChange();
      }

      @Override
      public void onEvent(@NotNull TimelineEvent event) {
      }
    });
    notifySamplerViewStateChange();
  }

  public void notifySamplerViewStateChange() {
    List<BaseMonitorView> runningViews = getRunningViews(myViews);
    if (runningViews.size() > 0 && (myTask == null || myTask.myLatch.getCount() == 0)) {
      myTask = new SamplerBackgroundTask(myProject, myViews);
      ProgressManager.getInstance().run(myTask);
    }
    else if (runningViews.size() == 0 && myTask != null) {
      myTask.exit();
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
          for (BaseMonitorView view : getRunningViews(myViews)) {
            view.getSampler().stop();
          }
          exit();
        }
      }
    }
  }
}

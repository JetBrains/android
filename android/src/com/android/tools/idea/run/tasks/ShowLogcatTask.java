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
package com.android.tools.idea.run.tasks;

import com.android.tools.idea.run.ShowLogcatListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowLogcatTask implements LaunchTask {
  private static final String ID = "SHOW_LOGCAT";
  @NotNull private final Project myProject;
  @Nullable private final String myApplicationId;

  public ShowLogcatTask(@NotNull Project project, @Nullable String applicationId) {
    myProject = project;
    myApplicationId = applicationId;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Activating Logcat Tool window";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.ASYNC_TASK;
  }

  @Override
  public void run(@NotNull LaunchContext launchContext) {
    myProject.getMessageBus().syncPublisher(ShowLogcatListener.TOPIC).showLogcat(launchContext.getDevice(), myApplicationId);
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }
}

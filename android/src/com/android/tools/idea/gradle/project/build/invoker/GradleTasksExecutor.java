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
package com.android.tools.idea.gradle.project.build.invoker;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Invokes Gradle tasks as a IDEA task in the background.
 */
public abstract class GradleTasksExecutor extends Task.Backgroundable {
  @NotNull public static final NotificationGroup LOGGING_NOTIFICATION =
    NotificationGroup.logOnlyGroup("Gradle Build (Logging)", PluginId.getId("org.jetbrains.android"));
  @NotNull public static final NotificationGroup BALLOON_NOTIFICATION =
    NotificationGroup.balloonGroup("Gradle Build (Balloon)", PluginId.getId("org.jetbrains.android"));

  protected GradleTasksExecutor(@Nullable Project project) {
    super(project, "Gradle Build Running", true);
  }
}

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
package com.android.tools.idea.gradle.project.sync.messages;

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID;

import com.android.tools.idea.project.messages.AbstractSyncMessages;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Service that collects and displays in the "Messages" tool window, post-sync project setup messages (errors, warnings, etc.)
 */
public class GradleSyncMessages extends AbstractSyncMessages {
  @NotNull
  public static GradleSyncMessages getInstance(@NotNull Project project) {
    return project.getService(GradleSyncMessages.class);
  }

  public GradleSyncMessages(@NotNull Project project) {
    super(project);
  }

  @Override
  @NotNull
  protected ProjectSystemId getProjectSystemId() {
    return GRADLE_SYSTEM_ID;
  }
}

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

import com.android.tools.idea.project.messages.AbstractSyncMessages;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker.VERSION_COMPATIBILITY_ISSUE_GROUP;
import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.*;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;

/**
 * Service that collects and displays in the "Messages" tool window, post-sync project setup messages (errors, warnings, etc.)
 */
public class GradleSyncMessages extends AbstractSyncMessages {
  @NotNull
  public static GradleSyncMessages getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSyncMessages.class);
  }

  public GradleSyncMessages(@NotNull Project project, @NotNull ExternalSystemNotificationManager manager) {
    super(project, manager);
  }

  @Override
  @NotNull
  protected ProjectSystemId getProjectSystemId() {
    return GRADLE_SYSTEM_ID;
  }

  public void removeProjectMessages() {
    removeMessages(PROJECT_STRUCTURE_ISSUES, MISSING_DEPENDENCIES, VARIANT_SELECTION_CONFLICTS, GENERATED_SOURCES,
                   VERSION_COMPATIBILITY_ISSUE_GROUP, SyncMessage.DEFAULT_GROUP);
  }
}

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
package com.android.tools.idea.gradle.project.sync.setup.post;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.build.BuildStatus.SKIPPED;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_CACHED_SETUP_FAILED;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static java.lang.System.currentTimeMillis;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.events.AndroidSyncFailure;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.Failure;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PostSyncProjectSetup {
  @NotNull private final Project myProject;
  @NotNull private final GradleSyncState mySyncState;

  @NotNull
  public static PostSyncProjectSetup getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostSyncProjectSetup.class);
  }

  @SuppressWarnings("unused") // Instantiated by IDEA
  public PostSyncProjectSetup(@NotNull Project project,
                              @NotNull GradleSyncState syncState) {
    myProject = project;
    mySyncState = syncState;
  }

  public void notifySyncFinished(@NotNull Request request) {
    // Notify "sync end" event first, to register the timestamp. Otherwise the cache (ProjectBuildFileChecksums) will store the date of the
    // previous sync, and not the one from the sync that just ended.
    if (request.usingCachedGradleModels) {
      long timestamp = currentTimeMillis();
      mySyncState.syncSkipped(timestamp, null);
      GradleBuildState.getInstance(myProject).buildFinished(SKIPPED);
    }
    else {
      if (mySyncState.lastSyncFailed()) {
        mySyncState.syncFailed("", null, null);
      }
      else {
        mySyncState.syncSucceeded();
      }
      ProjectBuildFileChecksums.saveToDisk(myProject);
    }
  }

  /**
   * Create a new {@link ExternalSystemTaskId} to be used while doing project setup from cache and adds a StartBuildEvent to build view.
   *
   * @param project
   * @return
   */
  @NotNull
  public static ExternalSystemTaskId createProjectSetupFromCacheTaskWithStartMessage(Project project) {
    // Create taskId
    ExternalSystemTaskId taskId = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project);

    // Create StartBuildEvent
    String workingDir = toCanonicalPath(getBaseDirPath(project).getPath());
    DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(taskId, "Project setup", workingDir, currentTimeMillis());
    SyncViewManager syncManager = ServiceManager.getService(project, SyncViewManager.class);
    syncManager.onEvent(taskId, new StartBuildEventImpl(buildDescriptor, "reading from cache..."));
    return taskId;
  }

  public static class Request {
    public boolean usingCachedGradleModels;
    public long lastSyncTimestamp = -1L;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request request = (Request)o;
      return usingCachedGradleModels == request.usingCachedGradleModels &&
             lastSyncTimestamp == request.lastSyncTimestamp;
    }

    @Override
    public int hashCode() {
      return Objects.hash(usingCachedGradleModels, lastSyncTimestamp);
    }
  }
}

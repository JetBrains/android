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
package com.android.tools.idea.gradle;

import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Listens for Gradle project import start/end events and updates the "Build Variant" tool window when necessary.
 */
public class GradleImportNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  private static final Key<Long> PROJECT_LAST_SYNC_TIMESTAMP_KEY = Key.create("android.gradle.project.last.sync.timestamp");

  private static GradleImportNotificationListener ourInstance;

  static {
    attachToManager();
  }

  /**
   * Attaches a singleton instance of itself to the IDE's {@link ExternalSystemProgressNotificationManager} service.
   * </p>
   * This method should be invoked on these events, to ensure that this listener is always attached.
   * <ul>
   * <li>When starting the IDE, in case there is an Android-Gradle project already open. We want to register this listener *before*
   * the automatic Gradle model synchronization is started.</li>
   * <li>When creating a new Android-Gradle project.</li>
   * <li>When importing an existing Android-Gradle project.</li>
   * </ul>
   */
  private static void attachToManager() {
    ourInstance = new GradleImportNotificationListener();
    ExternalSystemProgressNotificationManager notificationManager =
      ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    notificationManager.addNotificationListener(ourInstance);
  }

  private volatile boolean myProjectImportInProgress;
  private volatile boolean myInitialized;

  public static boolean isProjectImportInProgress() {
    return ourInstance != null && ourInstance.myProjectImportInProgress;
  }

  public static boolean isInitialized() {
    return ourInstance != null && ourInstance.myInitialized;
  }

  @Override
  public void onStart(@NotNull ExternalSystemTaskId id) {
    if (resolvingProject(id)) {
      if (myProjectImportInProgress) {
        return;
      }
      myProjectImportInProgress = true;
      myInitialized = true;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!myProjectImportInProgress) {
            return;
          }
          Projects.applyToCurrentGradleProject(new Consumer<Project>() {
            @Override
            public void consume(Project project) {
              EditorNotifications.getInstance(project).updateAllNotifications();
              BuildVariantView.getInstance(project).projectImportStarted();
            }
          });
        }
      });
    }
  }

  @Override
  public void onEnd(@NotNull ExternalSystemTaskId id) {
    if (resolvingProject(id)) {
      if (!myProjectImportInProgress) {
        return;
      }
      myProjectImportInProgress = false;
    }
  }

  public static void updateLastSyncTimestamp(@NotNull Project project) {
    project.putUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY, System.currentTimeMillis());
  }

  private static boolean resolvingProject(ExternalSystemTaskId id) {
    return GradleConstants.SYSTEM_ID.getId().equals(id.getProjectSystemId().getId()) &&
           id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT;
  }

  public static long getLastGradleSyncTimestamp(@NotNull Project project) {
    Long timestamp = project.getUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY);
    return timestamp != null ? timestamp.longValue() : -1L;
  }
}

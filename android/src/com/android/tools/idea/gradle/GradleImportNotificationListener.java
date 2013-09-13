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
import com.android.tools.idea.rendering.ModuleSetResourceRepository;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for Gradle project import start/end events and updates the "Build Variant" tool window when necessary.
 */
public class GradleImportNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
  private static GradleImportNotificationListener ourInstance;

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
  public static void attachToManager() {
    if (ourInstance != null) {
      return;
    }
    ourInstance = new GradleImportNotificationListener();
    ExternalSystemProgressNotificationManager progressNotificationManager = getExternalSystemProgressNotificationManager();
    progressNotificationManager.addNotificationListener(ourInstance);
  }

  private volatile boolean myProjectImportInProgress;

  public static void detachFromManager() {
    if (ourInstance == null) {
      return;
    }
    ExternalSystemProgressNotificationManager progressNotificationManager = getExternalSystemProgressNotificationManager();
    progressNotificationManager.removeNotificationListener(ourInstance);
    ourInstance = null;
  }

  public static boolean isProjectImportInProgress() {
    return ourInstance != null && ourInstance.myProjectImportInProgress;
  }

  @NotNull
  private static ExternalSystemProgressNotificationManager getExternalSystemProgressNotificationManager() {
    return ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
  }

  @Override
  public void onStart(@NotNull ExternalSystemTaskId id) {
    if (resolvingProject(id)) {
      if (myProjectImportInProgress) {
        return;
      }
      myProjectImportInProgress = true;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!myProjectImportInProgress) {
            return;
          }
          Project project = Projects.getCurrentGradleProject();
          if (project != null) {
            BuildVariantView.getInstance(project).projectImportStarted();
          }
        }
      });
    }
  }

  private static boolean resolvingProject(ExternalSystemTaskId id) {
    return ExternalSystemTaskType.RESOLVE_PROJECT.equals(id.getType());
  }

  @Override
  public void onEnd(@NotNull ExternalSystemTaskId id) {
    if (resolvingProject(id)) {
      if (!myProjectImportInProgress) {
        return;
      }
      myProjectImportInProgress = false;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Project project = Projects.getCurrentGradleProject();
          if (project != null) {
            BuildVariantView.getInstance(project).updateContents();
            ModuleSetResourceRepository.moduleRootsChanged(project);
          }
        }
      });
    }
  }
}

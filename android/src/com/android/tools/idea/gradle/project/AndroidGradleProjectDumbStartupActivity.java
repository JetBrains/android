/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.JpsBuildContext;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

public class AndroidGradleProjectDumbStartupActivity implements StartupActivity.DumbAware {
  // Copy of a private constant in GradleNotification.java.
  private static final String GRADLE_NOTIFICATION_GROUP_NAME = "Gradle Notification Group";

  @Override
  public void runActivity(@NotNull Project project) {
    // Disable Gradle plugin notifications in Android Studio.
    if (IdeInfo.getInstance().isAndroidStudio()) {
      disableGradlePluginNotifications();
    }

    // Register a task that gets notified when a Gradle-based Android project is compiled via JPS.
    registerAfterTaskForAndroidGradleProjectCompiledViaJPS(project);

    // Register a task that gets notified when a Gradle-based Android project is compiled via direct Gradle invocation.
    registerAfterTaskForAndroidGradleProjectCompiledViaGradleInvocation(project);
  }

  private void registerAfterTaskForAndroidGradleProjectCompiledViaGradleInvocation(Project project) {
    GradleBuildInvoker.getInstance(project).add(result -> {
      if (project.isDisposed()) return;
      PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(result);
      GradleBuildContext newContext = new GradleBuildContext(result);
      AndroidProjectBuildNotifications.getInstance(project).notifyBuildComplete(newContext);

      // Force VFS refresh required by any of the modules.
      if (isVfsRefreshAfterBuildRequired(project)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          FileDocumentManager.getInstance().saveAllDocuments();
          SaveAndSyncHandler.getInstance().refreshOpenFiles();
          VirtualFileManager.getInstance().refreshWithoutFileWatcher(true /* asynchronously */);
        });
      }
    });
  }

  private void registerAfterTaskForAndroidGradleProjectCompiledViaJPS(Project project) {
    CompilerManager.getInstance(project).addAfterTask(context -> {
      if (GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
        PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(context);

        JpsBuildContext newContext = new JpsBuildContext(context);
        AndroidProjectBuildNotifications.getInstance(project).notifyBuildComplete(newContext);
      }
      return true;
    });
  }

  private void disableGradlePluginNotifications() {
    NotificationsConfiguration
      .getNotificationsConfiguration()
      .changeSettings(GRADLE_NOTIFICATION_GROUP_NAME, NotificationDisplayType.NONE, false, false);
  }

  /**
   * @return {@code true} if any of the modules require VFS refresh after build.
   */
  private static boolean isVfsRefreshAfterBuildRequired(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
      if (androidModuleModel != null && androidModuleModel.getFeatures().isVfsRefreshAfterBuildRequired()) {
        return true;
      }
    }
    return false;
  }
}

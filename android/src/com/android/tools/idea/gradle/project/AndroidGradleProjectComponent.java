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
package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.JpsBuildContext;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

public class AndroidGradleProjectComponent implements ProjectComponent {
  // Copy of a private constant in GradleNotification.java.
  private static final String GRADLE_NOTIFICATION_GROUP_NAME = "Gradle Notification Group";
  @NotNull private final Project myProject;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final LegacyAndroidProjects myLegacyAndroidProjects;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    AndroidGradleProjectComponent component = project.getComponent(AndroidGradleProjectComponent.class);
    assert component != null;
    return component;
  }

  public AndroidGradleProjectComponent(@NotNull Project project) {
    myProject = project;
    myGradleProjectInfo = GradleProjectInfo.getInstance(project);
    myLegacyAndroidProjects = new LegacyAndroidProjects(project);

    // Disable Gradle plugin notifications in Android Studio.
    if (IdeInfo.getInstance().isAndroidStudio()) {
      NotificationsConfiguration
        .getNotificationsConfiguration()
        .changeSettings(GRADLE_NOTIFICATION_GROUP_NAME, NotificationDisplayType.NONE, false, false);
    }


    // Register a task that gets notified when a Gradle-based Android project is compiled via JPS.
    CompilerManager.getInstance(project).addAfterTask(context -> {
      if (myGradleProjectInfo.isBuildWithGradle()) {
        PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(context);

        JpsBuildContext newContext = new JpsBuildContext(context);
        AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);
      }
      return true;
    });

    // Register a task that gets notified when a Gradle-based Android project is compiled via direct Gradle invocation.
    GradleBuildInvoker.getInstance(project).add(result -> {
      if (myProject.isDisposed()) return;
      PostProjectBuildTasksExecutor.getInstance(myProject).onBuildCompletion(result);
      GradleBuildContext newContext = new GradleBuildContext(result);
      AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);

      // Force VFS refresh required by any of the modules.
      if (isVfsRefreshAfterBuildRequired(myProject)) {
        ApplicationManager.getApplication().invokeLater(() -> {
          FileDocumentManager.getInstance().saveAllDocuments();
          SaveAndSyncHandler.getInstance().refreshOpenFiles();
          VirtualFileManager.getInstance().refreshWithoutFileWatcher(true /* asynchronously */);
        });
      }
    });
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

  /**
   * This method is called when a project is created and when it is opened.
   */
  @Override
  public void projectOpened() {
    IdeInfo ideInfo = IdeInfo.getInstance();
    if (ideInfo.isAndroidStudio()) {
      ExternalSystemProjectTrackerSettings.getInstance(myProject).setAutoReloadType(ExternalSystemProjectTrackerSettings.AutoReloadType.NONE);
    }
    AndroidProjectInfo androidProjectInfo = AndroidProjectInfo.getInstance(myProject);
    if (ideInfo.isAndroidStudio() && androidProjectInfo.isLegacyIdeaAndroidProject() && !androidProjectInfo.isApkProject()) {
      myLegacyAndroidProjects.trackProject();
      if (!myGradleProjectInfo.isBuildWithGradle()) {
        // Suggest that Android Studio users use Gradle instead of IDEA project builder.
        myLegacyAndroidProjects.showMigrateToGradleWarning();
      }
    }
    // Check if the Gradle JDK environment variable is valid
    if (ideInfo.isAndroidStudio()) {
      IdeSdks ideSdks = IdeSdks.getInstance();
      if (ideSdks.isJdkEnvVariableDefined() && !ideSdks.isJdkEnvVariableValid()) {
        String msg = JDK_LOCATION_ENV_VARIABLE_NAME + " is being ignored since it is set to an invalid JDK Location:\n"
                     + ideSdks.getEnvVariableJdkValue();
        AndroidNotification.getInstance(myProject).showBalloon("", msg, NotificationType.WARNING,
                                                               SelectJdkFromFileSystemHyperlink.create(myProject));
      }
    }
  }
}

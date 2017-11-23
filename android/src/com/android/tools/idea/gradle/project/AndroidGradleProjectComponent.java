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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.JpsBuildContext;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.startup.DelayedInitialization;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleProjects.canImportAsGradleProject;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED;
import static com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class AndroidGradleProjectComponent extends AbstractProjectComponent {
  @NotNull private final LegacyAndroidProjects myLegacyAndroidProjects;

  @Nullable private Disposable myDisposable;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    AndroidGradleProjectComponent component = project.getComponent(AndroidGradleProjectComponent.class);
    assert component != null;
    return component;
  }

  public AndroidGradleProjectComponent(@NotNull Project project) {
    this(project, new LegacyAndroidProjects(project));
  }

  public AndroidGradleProjectComponent(@NotNull Project project, @NotNull LegacyAndroidProjects legacyAndroidProjects) {
    super(project);
    myLegacyAndroidProjects = legacyAndroidProjects;

    // Register a task that gets notified when a Gradle-based Android project is compiled via JPS.
    CompilerManager.getInstance(myProject).addAfterTask(context -> {
      if (GradleProjectInfo.getInstance(myProject).isBuildWithGradle()) {
        PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(context);

        JpsBuildContext newContext = new JpsBuildContext(context);
        AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);
      }
      return true;
    });

    // Register a task that gets notified when a Gradle-based Android project is compiled via direct Gradle invocation.
    GradleBuildInvoker.getInstance(myProject).add(result -> {
      PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(result);
      GradleBuildContext newContext = new GradleBuildContext(result);
      AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);
    });
  }

  /**
   * This method is called when a project is created and when it is opened.
   */
  @Override
  public void projectOpened() {
    GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(myProject);
    if (myProject.isOpen()) {
      String error = gradleProjectInfo.getProjectCreationError();
      if (isNotEmpty(error)) {
        // http://b/62543339
        // If we have a "project creation" error, it means that an error occurred when syncing a project that just created with the NPW.
        // The error was ignored by the IDEA's Gradle infrastructure. Here we report the error.
        ExternalSystemNotificationManager notificationManager = ExternalSystemNotificationManager.getInstance(myProject);
        // http://b/62761000
        // The new exception must be an instance of ExternalSystemException, otherwise "quick fixes" will not show up.
        // GradleNotificationExtension only recognizes a few exception types when decided whether a "quick fix" should be displayed.
        Runnable processError = () -> notificationManager.processExternalProjectRefreshError(new ExternalSystemException(error),
                                                                                           myProject.getName(), GradleConstants.SYSTEM_ID);
        // http://b/66911744: Some quickfixes may need to access PSI (e.g., build files parsing), and that might not work
        // if the project is not yet initialised. So ensure the project is initialised before sync error handling mechanism launches.
        StartupManager.getInstance(myProject).runWhenProjectIsInitialized(processError);
        gradleProjectInfo.setProjectCreationError(null);
      }
    }

    SupportedModuleChecker.getInstance().checkForSupportedModules(myProject);
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (syncState.isSyncInProgress()) {
      // when opening a new project, the UI was not updated when sync started. Updating UI ("Build Variants" tool window, "Sync" toolbar
      // button and editor notifications.
      syncState.notifyStateChanged();
    }

    IdeInfo ideInfo = IdeInfo.getInstance();
    AndroidProjectInfo androidProjectInfo = AndroidProjectInfo.getInstance(myProject);
    if (ideInfo.isAndroidStudio() && androidProjectInfo.isLegacyIdeaAndroidProject() && !androidProjectInfo.isApkProject()) {
      myLegacyAndroidProjects.trackProject();
      // Suggest that Android Studio users use Gradle instead of IDEA project builder.
      myLegacyAndroidProjects.showMigrateToGradleWarning();
      return;
    }

    if (gradleProjectInfo.isBuildWithGradle()) {
      configureGradleProject();
    }
    else if (ideInfo.isAndroidStudio() && myProject.getBaseDir() != null && canImportAsGradleProject(myProject.getBaseDir())) {
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(myProject, TRIGGER_PROJECT_LOADED, null);
    }
  }

  public void configureGradleProject() {
    if (myDisposable != null) {
      return;
    }
    myDisposable = Disposer.newDisposable();

    // Prevent IDEA from refreshing project. We will do it ourselves in AndroidGradleProjectStartupActivity.
    myProject.putUserData(NEWLY_IMPORTED_PROJECT, Boolean.TRUE);

    List<Class<? extends RunConfigurationProducer<?>>> runConfigurationProducerTypes = new ArrayList<>();
    runConfigurationProducerTypes.add(AllInPackageGradleConfigurationProducer.class);
    runConfigurationProducerTypes.add(TestClassGradleConfigurationProducer.class);
    runConfigurationProducerTypes.add(TestMethodGradleConfigurationProducer.class);

    RunConfigurationProducerService runConfigurationProducerManager = RunConfigurationProducerService.getInstance(myProject);
    if (IdeInfo.getInstance().isAndroidStudio()) {
      // Make sure the gradle test configurations are ignored in this project. This will modify .idea/runConfigurations.xml
      for (Class<? extends RunConfigurationProducer<?>> type : runConfigurationProducerTypes) {
        runConfigurationProducerManager.getState().ignoredProducers.add(type.getName());
      }
    }
    else {
      // Make sure the gradle test configurations are not ignored in this project, since they already work in Android gradle projects. This
      // will modify .idea/runConfigurations.xml
      for (Class<? extends RunConfigurationProducer<?>> type : runConfigurationProducerTypes) {
        runConfigurationProducerManager.getState().ignoredProducers.remove(type.getName());
      }
    }
  }

  @Override
  public void projectClosed() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }
}

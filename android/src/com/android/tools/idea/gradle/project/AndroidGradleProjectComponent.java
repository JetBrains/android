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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.JpsBuildContext;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.importing.OpenMigrationToGradleUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.apk.ApkProjects.isApkProject;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.stats.AndroidStudioUsageTracker.anonymizeUtf8;

public class AndroidGradleProjectComponent extends AbstractProjectComponent {
  @NonNls private static final String SHOW_MIGRATE_TO_GRADLE_POPUP = "show.migrate.to.gradle.popup";

  @Nullable private Disposable myDisposable;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    AndroidGradleProjectComponent component = project.getComponent(AndroidGradleProjectComponent.class);
    assert component != null;
    return component;
  }

  public AndroidGradleProjectComponent(@NotNull Project project) {
    super(project);

    // Register a task that gets notified when a Gradle-based Android project is compiled via JPS.
    CompilerManager.getInstance(myProject).addAfterTask(context -> {
      if (isBuildWithGradle(myProject)) {
        PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(context);

        JpsBuildContext newContext = new JpsBuildContext(context);
        AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);
      }
      return true;
    });

    // Register a task that gets notified when a Gradle-based Android project is compiled via direct Gradle invocation.
    GradleBuildInvoker.getInstance(myProject).add(result -> {
      if (myProject.isDisposed()) return;
      PostProjectBuildTasksExecutor.getInstance(myProject).onBuildCompletion(result);
      GradleBuildContext newContext = new GradleBuildContext(result);
      AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);

      // Force a refresh.
      // https://code.google.com/p/android/issues/detail?id=229633
      ApplicationManager.getApplication().invokeLater(() -> {
        FileDocumentManager.getInstance().saveAllDocuments();
        SaveAndSyncHandler.getInstance().refreshOpenFiles();
        VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
      });
    });
  }

  /**
   * This method is called when a project is created and when it is opened.
   */
  @Override
  public void projectOpened() {
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (syncState.isSyncInProgress()) {
      // when opening a new project, the UI was not updated when sync started. Updating UI ("Build Variants" tool window, "Sync" toolbar
      // button and editor notifications.
      syncState.notifyStateChanged();
    }
    if (isLegacyIdeaAndroidProject(myProject) && !isApkProject(myProject)) {
      trackLegacyIdeaAndroidProject();
      if (shouldShowMigrateToGradleNotification()) {
        // Suggest that Android Studio users use Gradle instead of IDEA project builder.
        showMigrateToGradleWarning();
      }
      return;
    }

    boolean isGradleProject = isBuildWithGradle(myProject);
    if (isGradleProject) {
      configureGradleProject();
    }
    else if (IdeInfo.getInstance().isAndroidStudio() && myProject.getBaseDir() != null && canImportAsGradleProject(myProject.getBaseDir())) {
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(myProject, null);
    }
  }

  private boolean shouldShowMigrateToGradleNotification() {
    return PropertiesComponent.getInstance(myProject).getBoolean(SHOW_MIGRATE_TO_GRADLE_POPUP, true);
  }

  private void trackLegacyIdeaAndroidProject() {
    if (!UsageTracker.getInstance().getAnalyticsSettings().hasOptedIn()) {
      return;
    }

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
      String packageName = null;

      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      for (Module module : moduleManager.getModules()) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null && !facet.requiresAndroidModel()) {
          if (facet.isAppProject()) {
            // Prefer the package name from an app module.
            packageName = getPackageNameInLegacyIdeaAndroidModule(facet);
            if (packageName != null) {
              break;
            }
          }
          else if (packageName == null) {
            String modulePackageName = getPackageNameInLegacyIdeaAndroidModule(facet);
            if (modulePackageName != null) {
              packageName = modulePackageName;
            }
          }
        }
        if (packageName != null) {
          AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder().setCategory(EventCategory.GRADLE)
                                                                            .setKind(EventKind.LEGACY_IDEA_ANDROID_PROJECT)
                                                                            .setProjectId(anonymizeUtf8(packageName));
          UsageTracker.getInstance().log(event);
        }
      }
    });
  }

  @Nullable
  private static String getPackageNameInLegacyIdeaAndroidModule(@NotNull AndroidFacet facet) {
    // This invocation must happen after the project has been initialized.
    Manifest manifest = facet.getManifest();
    return manifest != null ? manifest.getPackage().getValue() : null;
  }

  private void showMigrateToGradleWarning() {
    String errMsg = "This project does not use the Gradle build system. We recommend that you migrate to using the Gradle build system.";
    NotificationHyperlink moreInfoHyperlink = new OpenMigrationToGradleUrlHyperlink().setCloseOnClick(true);
    NotificationHyperlink doNotShowAgainHyperlink = new NotificationHyperlink("do.not.show", "Don't show this message again.") {
      @Override
      protected void execute(@NotNull Project project) {
        PropertiesComponent.getInstance(myProject).setValue(SHOW_MIGRATE_TO_GRADLE_POPUP, Boolean.FALSE.toString());
      }
    };

    AndroidGradleNotification notification = AndroidGradleNotification.getInstance(myProject);
    notification.showBalloon("Migrate Project to Gradle?", errMsg, NotificationType.WARNING, moreInfoHyperlink, doNotShowAgainHyperlink);
  }

  public void configureGradleProject() {
    if (myDisposable != null) {
      return;
    }
    myDisposable = () -> {
    };

    // Prevent IDEA from refreshing project. We will do it ourselves in AndroidGradleProjectStartupActivity.
    myProject.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);

    List<Class<? extends RunConfigurationProducer<?>>> runConfigurationProducerTypes = new ArrayList<>();
    runConfigurationProducerTypes.add(AllInPackageGradleConfigurationProducer.class);
    runConfigurationProducerTypes.add(TestClassGradleConfigurationProducer.class);
    runConfigurationProducerTypes.add(TestMethodGradleConfigurationProducer.class);

    if (IdeInfo.getInstance().isAndroidStudio()) {
      // Make sure the gradle test configurations are ignored in this project. This will modify .idea/runConfigurations.xml
      ignore(runConfigurationProducerTypes);
    }
    else {
      // Make sure the gradle test configurations are not ignored in this project, since they already work in Android gradle projects. This
      // will modify .idea/runConfigurations.xml
      doNotIgnore(runConfigurationProducerTypes);
    }
  }

  private void ignore(@NotNull List<Class<? extends RunConfigurationProducer<?>>> runConfigurationProducerTypes) {
    RunConfigurationProducerService runConfigurationProducerManager = RunConfigurationProducerService.getInstance(myProject);
    for (Class<? extends RunConfigurationProducer<?>> type : runConfigurationProducerTypes) {
      runConfigurationProducerManager.getState().ignoredProducers.add(type.getName());
    }
  }

  private void doNotIgnore(@NotNull List<Class<? extends RunConfigurationProducer<?>>> runConfigurationProducerTypes) {
    RunConfigurationProducerService runConfigurationProducerManager = RunConfigurationProducerService.getInstance(myProject);
    for (Class<? extends RunConfigurationProducer<?>> type : runConfigurationProducerTypes) {
      runConfigurationProducerManager.getState().ignoredProducers.remove(type.getName());
    }
  }

  @Override
  public void projectClosed() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }
}

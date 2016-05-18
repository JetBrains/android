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

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.JpsBuildContext;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.stats.UsageTracker;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Function;
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

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY;
import static com.intellij.openapi.util.text.StringUtil.join;

public class AndroidGradleProjectComponent extends AbstractProjectComponent {
  @NonNls private static final String SHOW_MIGRATE_TO_GRADLE_POPUP = "show.migrate.to.gradle.popup";

  @Nullable private Disposable myDisposable;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    AndroidGradleProjectComponent component = project.getComponent(AndroidGradleProjectComponent.class);
    assert component != null;
    return component;
  }

  public AndroidGradleProjectComponent(@NotNull final Project project) {
    super(project);

    // Register a task that gets notified when a Gradle-based Android project is compiled via JPS.
    CompilerManager.getInstance(myProject).addAfterTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        if (isBuildWithGradle(myProject)) {
          PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(context);

          JpsBuildContext newContext = new JpsBuildContext(context);
          AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);
        }
        return true;
      }
    });

    // Register a task that gets notified when a Gradle-based Android project is compiled via direct Gradle invocation.
    GradleInvoker.getInstance(myProject).addAfterGradleInvocationTask(new GradleInvoker.AfterGradleInvocationTask() {
      @Override
      public void execute(@NotNull GradleInvocationResult result) {
        PostProjectBuildTasksExecutor.getInstance(project).onBuildCompletion(result);

        GradleBuildContext newContext = new GradleBuildContext(result);
        AndroidProjectBuildNotifications.getInstance(myProject).notifyBuildComplete(newContext);
      }
    });
  }

  /**
   * This method is called when a project is created and when it is opened.
   */
  @Override
  public void projectOpened() {
    checkForSupportedModules();
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (syncState.isSyncInProgress()) {
      // when opening a new project, the UI was not updated when sync started. Updating UI ("Build Variants" tool window, "Sync" toolbar
      // button and editor notifications.
      syncState.notifyUser();
    }
    if (isAndroidStudio() && isLegacyIdeaAndroidProject(myProject)) {
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
    else if (isAndroidStudio() && myProject.getBaseDir() != null && canImportAsGradleProject(myProject.getBaseDir())) {
      GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
    }
  }

  private boolean shouldShowMigrateToGradleNotification() {
    return PropertiesComponent.getInstance(myProject).getBoolean(SHOW_MIGRATE_TO_GRADLE_POPUP, true);
  }

  private void trackLegacyIdeaAndroidProject() {
    if (!UsageTracker.getInstance().canTrack()) {
      return;
    }

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        String packageName = null;

        ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        for (Module module : moduleManager.getModules()) {
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet != null && !facet.requiresAndroidModel()) {
            boolean library = facet.isLibraryProject();
            if (!library) {
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
        }
        if (packageName != null) {
          UsageTracker.getInstance().trackLegacyIdeaAndroidProject(packageName);
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
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    // Prevent IDEA from refreshing project. We will do it ourselves in AndroidGradleProjectStartupActivity.
    myProject.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);

    enforceExternalBuild(myProject);

    // Make sure the gradle test configurations are not ignored in this project, since they already work in Android gradle projects. This
    // will modify .idea/runConfigurations.xml
    RunConfigurationProducerService runConfigurationProducerManager = RunConfigurationProducerService.getInstance(myProject);
    runConfigurationProducerManager.getState().ignoredProducers.remove(AllInPackageGradleConfigurationProducer.class.getName());
    runConfigurationProducerManager.getState().ignoredProducers.remove(TestMethodGradleConfigurationProducer.class.getName());
    runConfigurationProducerManager.getState().ignoredProducers.remove(TestClassGradleConfigurationProducer.class.getName());
  }

  @Override
  public void projectClosed() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }

  /**
   * Verifies that the project, if it is an Android Gradle project, does not have any modules that are not known by Gradle. For example,
   * when adding a plain IDEA Java module.
   * Do not call this method from {@link ModuleListener#moduleAdded(Project, Module)} because the settings that this method look for are
   * not present when importing a valid Gradle-aware module, resulting in false positives.
   */
  public void checkForSupportedModules() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (modules.length == 0 || !isBuildWithGradle(myProject)) {
      return;
    }
    final List<Module> unsupportedModules = new ArrayList<Module>();

    for (Module module : modules) {
      final ModuleType moduleType = ModuleType.get(module);

      if (moduleType instanceof JavaModuleType) {
        final String externalSystemId = module.getOptionValue(EXTERNAL_SYSTEM_ID_KEY);

        if (!GRADLE_SYSTEM_ID.getId().equals(externalSystemId)) {
          unsupportedModules.add(module);
        }
      }
    }

    if (unsupportedModules.size() == 0) {
      return;
    }
    String s = join(unsupportedModules, new Function<Module, String>() {
      @Override
      public String fun(Module module) {
        return module.getName();
      }
    }, ", ");
    AndroidGradleNotification.getInstance(myProject).showBalloon(
      "Unsupported Modules Detected",
      "Compilation is not supported for following modules: " + s +
      ". Unfortunately you can't have non-Gradle Java modules and Android-Gradle modules in one project.",
      NotificationType.ERROR);
  }
}

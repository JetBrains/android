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
package com.android.tools.idea.gradle.util;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import javax.swing.*;
import java.io.File;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.VARIANT_SELECTION_CONFLICTS;

/**
 * Utility methods for {@link Project}s.
 */
public final class Projects {
  public static final Key<Boolean> HAS_UNRESOLVED_DEPENDENCIES = Key.create("has.unresolved.dependencies");

  private static final Logger LOG = Logger.getInstance(Projects.class);
  private static final Module[] NO_MODULES = new Module[0];

  private Projects() {
  }

  /**
   * Indicates whether the last sync with Gradle failed.
   */
  public static boolean lastGradleSyncFailed(@NotNull Project project) {
    return (!GradleSyncState.getInstance(project).isSyncInProgress() && isGradleProjectWithoutModel(project)) ||
           hasErrors(project);
  }

  public static boolean hasErrors(@NotNull Project project) {
    if (hasUnresolvedDependencies(project)) {
      return true;
    }
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
    int errorCount = messages.getErrorCount();
    if (errorCount > 0) {
      return false;
    }
    // Variant selection errors do not count as "sync failed" errors.
    int variantSelectionErrorCount = messages.getMessageCount(VARIANT_SELECTION_CONFLICTS);
    return errorCount != variantSelectionErrorCount;
  }

  private static boolean hasUnresolvedDependencies(@NotNull Project project) {
    Boolean val = project.getUserData(HAS_UNRESOLVED_DEPENDENCIES);
    return val != null && val.booleanValue();
  }

  /**
   * Indicates the given project is an Gradle-based Android project that does not contain any Gradle model. Possible causes for this
   * scenario to happen are:
   * <ul>
   * <li>the last sync with Gradle failed</li>
   * <li>Studio just started up and it has not synced the project yet</li>
   * </ul>
   *
   * @param project the project.
   * @return {@code true} if the project is a Gradle-based Android project that does not contain any Gradle model.
   */
  public static boolean isGradleProjectWithoutModel(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.isGradleProject() && facet.getIdeaAndroidProject() == null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Opens the given project in the IDE.
   *
   * @param project the project to open.
   */
  public static void open(@NotNull Project project) {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    ProjectUtil.updateLastProjectLocation(project.getBasePath());
    if (WindowManager.getInstance().isFullScreenSupportedInCurrentOS()) {
      IdeFocusManager instance = IdeFocusManager.findInstance();
      IdeFrame lastFocusedFrame = instance.getLastFocusedFrame();
      if (lastFocusedFrame instanceof IdeFrameEx) {
        boolean fullScreen = ((IdeFrameEx)lastFocusedFrame).isInFullScreen();
        if (fullScreen) {
          project.putUserData(IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN, Boolean.TRUE);
        }
      }
    }
    projectManager.openProject(project);
  }

  public static boolean isDirectGradleInvocationEnabled(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD;
  }

  public static boolean isOfflineBuildModeEnabled(@NotNull Project project) {
    return GradleSettings.getInstance(project).isOfflineWork();
  }

  public static boolean isGradleProject(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && androidFacet.isGradleProject()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Indicates whether the give project is a legacy IDEA Android project (which is deprecated in Android Studio.)
   *
   * @param project the given project.
   * @return {@code true} if the given project is a legacy IDEA Android project; {@code false} otherwise.
   */
  public static boolean isIdeaAndroidProject(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (AndroidFacet.getInstance(module) != null && AndroidGradleFacet.getInstance(module) == null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Ensures that "External Build" is enabled for the given Gradle-based project. External build is the type of build that delegates project
   * building to Gradle.
   *
   * @param project the given project. This method does not do anything if the given project is not a Gradle-based project.
   */
  public static void enforceExternalBuild(@NotNull Project project) {
    if (isGradleProject(project)) {
      // We only enforce JPS usage when the 'android' plug-in is not being used in Android Studio.
      if (!AndroidStudioSpecificInitializer.isAndroidStudio()) {
        AndroidGradleBuildConfiguration.getInstance(project).USE_EXPERIMENTAL_FASTER_BUILD = false;
      }
    }
  }

  /**
   * Returns the modules to build based on the current selection in the 'Project' tool window. If the module that corresponds to the project
   * is selected, all the modules in such projects are returned. If there is no selection, an empty array is returned.
   *
   * @param project     the given project.
   * @param dataContext knows the modules that are selected. If {@code null}, this method gets the {@code DataContext} from the 'Project'
   *                    tool window directly.
   * @return the modules to build based on the current selection in the 'Project' tool window.
   */
  @NotNull
  public static Module[] getModulesToBuildFromSelection(@NotNull Project project, @Nullable DataContext dataContext) {
    if (dataContext == null) {
      ProjectView projectView = ProjectView.getInstance(project);
      JComponent treeComponent = projectView.getCurrentProjectViewPane().getComponentToFocus();
      dataContext = DataManager.getInstance().getDataContext(treeComponent);
    }
    Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modules != null) {
      if (modules.length == 1 && isProjectModule(project, modules[0])) {
        return ModuleManager.getInstance(project).getModules();
      }
      return modules;
    }

    Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module != null) {
      return isProjectModule(project, module) ? ModuleManager.getInstance(project).getModules() : new Module[]{module};
    }

    return NO_MODULES;
  }

  private static boolean isProjectModule(@NotNull Project project, @NotNull Module module) {
    // if we got here is because we are dealing with a Gradle project, but if there is only one module selected and this module is the
    // module that corresponds to the project itself, it won't have an android-gradle facet. In this case we treat it as if we were going
    // to build the whole project.
    File moduleFilePath = new File(FileUtil.toSystemDependentName(module.getModuleFilePath()));
    File moduleRootDirPath = moduleFilePath.getParentFile();
    if (moduleRootDirPath == null) {
      return false;
    }
    return FileUtil.filesEqual(moduleRootDirPath, new File(project.getBasePath())) && AndroidGradleFacet.getInstance(module) == null;
  }

  /**
   * Indicates whether Gradle is used to build this project.
   * Note: {#isGradleProject()} indicates whether a project has a IdeaAndroidProject model. That method should be preferred in almost all
   * cases. Use this method only if you explicitly need to check whether the model was generated by Gradle (this will exclude models
   * generated by other build systems.)
   */
  public static boolean isBuildWithGradle(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (AndroidGradleFacet.getInstance(module) != null) {
        return true;
      }
    }
    return false;
  }
}

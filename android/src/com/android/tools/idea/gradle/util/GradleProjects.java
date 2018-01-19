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

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule;
import static com.intellij.openapi.util.io.FileUtil.pathsEqual;
import static com.intellij.openapi.wm.impl.IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN;
import static java.lang.Boolean.TRUE;

/**
 * Utility methods for {@link Project}s.
 */
public final class GradleProjects {
  private static final Key<Boolean> SYNC_REQUESTED_DURING_BUILD = Key.create("project.sync.requested.during.build");

  private GradleProjects() {
  }

  public static void executeProjectChanges(@NotNull Project project, @NotNull Runnable changes) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      if (!project.isDisposed()) {
        changes.run();
      }
      return;
    }
    ApplicationManager.getApplication().invokeAndWait(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      if (!project.isDisposed()) {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(changes);
      }
    }));
  }

  /**
   * Opens the given project in the IDE.
   *
   * @param project the project to open.
   */
  public static void open(@NotNull Project project) {
    updateLastProjectLocation(project.getBasePath());
    if (WindowManager.getInstance().isFullScreenSupportedInCurrentOS()) {
      IdeFocusManager instance = IdeFocusManager.findInstance();
      IdeFrame lastFocusedFrame = instance.getLastFocusedFrame();
      if (lastFocusedFrame instanceof IdeFrameEx) {
        boolean fullScreen = ((IdeFrameEx)lastFocusedFrame).isInFullScreen();
        if (fullScreen) {
          project.putUserData(SHOULD_OPEN_IN_FULL_SCREEN, TRUE);
        }
      }
    }
    ProjectManagerEx.getInstanceEx().openProject(project);
  }

  public static boolean isOfflineBuildModeEnabled(@NotNull Project project) {
    return GradleSettings.getInstance(project).isOfflineWork();
  }

  @Nullable
  public static AndroidModel getAndroidModel(@NotNull Module module) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    return androidFacet != null ? androidFacet.getConfiguration().getModel() : null;
  }

  /**
   * Returns the modules to build based on the current selection in the 'Project' tool window. If the module that corresponds to the project
   * is selected, all the modules in such projects are returned. If there is no selection, an empty array is returned.
   *
   * @param project     the given project.
   * @param dataContext knows the modules that are selected. If {@code null}, this method gets the {@code DataContext} from the 'Project'
   *                    tool window directly.
   * @return the modules to build based on the current selection in the 'Project' tool window.
   * @deprecated use {@link GradleProjectInfo#getModulesToBuildFromSelection(DataContext)}
   */
  @Deprecated
  @NotNull
  public static Module[] getModulesToBuildFromSelection(@NotNull Project project, @Nullable DataContext dataContext) {
    return GradleProjectInfo.getInstance(project).getModulesToBuildFromSelection(dataContext);
  }

  @Nullable
  public static File findModuleRootFolderPath(@NotNull Module module) {
    File moduleFilePath = toSystemDependentPath(module.getModuleFilePath());
    return moduleFilePath.getParentFile();
  }

  /**
   * Indicates whether Gradle is used to build this project.
   * Note: {@link AndroidProjectInfo#requiresAndroidModel()} indicates whether a project requires an {@link AndroidModel}.
   * That method should be preferred in almost all cases. Use this method only if you explicitly need to check whether the model is
   * Gradle-specific.
   *
   * @deprecated use {@link GradleProjectInfo#isBuildWithGradle()}
   */
  // TODO remove this method and update clients to use GradleProjectInfo instead.
  @Deprecated
  public static boolean isBuildWithGradle(@NotNull Project project) {
    return GradleProjectInfo.getInstance(project).isBuildWithGradle();
  }

  /**
   * Indicates whether the given module is the one that represents the project.
   * <p>
   * For example, in this project:
   * <pre>
   * project1
   * - module1
   *   - module1.iml
   * - module2
   *   - module2.iml
   * -project1.iml
   * </pre>
   * "project1" is the module that represents the project.
   * </p>
   *
   * @param module the given module.
   * @return {@code true} if the given module is the one that represents the project, {@code false} otherwise.
   */
  public static boolean isGradleProjectModule(@NotNull Module module) {
    if (!isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
      return false;
    }

    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null && androidFacet.requiresAndroidModel() && GradleFacet.isAppliedTo(module)) {
      // If the module is an Android project, check that the module's path is the same as the project's.
      File moduleFilePath = toSystemDependentPath(module.getModuleFilePath());
      File moduleRootDirPath = moduleFilePath.getParentFile();
      return pathsEqual(moduleRootDirPath.getPath(), module.getProject().getBasePath());
    }
    // For non-Android project modules, the top-level one is the one without an "Android-Gradle" facet.
    return !GradleFacet.isAppliedTo(module);
  }

  /**
   * Indicates whether the project in the given folder can be imported as a Gradle project.
   *
   * @param importSource the folder containing the project.
   * @return {@code true} if the project can be imported as a Gradle project, {@code false} otherwise.
   */
  public static boolean canImportAsGradleProject(@NotNull VirtualFile importSource) {
    VirtualFile target = findImportTarget(importSource);
    return target != null && GradleConstants.EXTENSION.equals(target.getExtension());
  }

  public static void setSyncRequestedDuringBuild(@NotNull Project project, @Nullable Boolean value) {
    project.putUserData(SYNC_REQUESTED_DURING_BUILD, value);
  }

  public static boolean isSyncRequestedDuringBuild(@NotNull Project project) {
    return SYNC_REQUESTED_DURING_BUILD.get(project, false);
  }

  public static boolean isIdeaAndroidModule(@NotNull Module module) {
    if (GradleFacet.getInstance(module) != null || JavaFacet.getInstance(module) != null) {
      return true;
    }
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null && androidFacet.requiresAndroidModel()) {
      return true;
    }
    return false;
  }
}

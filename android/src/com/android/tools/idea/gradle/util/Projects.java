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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.common.collect.Maps;
import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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

import javax.swing.*;
import java.io.File;
import java.util.Map;

import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;
import static com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.isExternalSystemAwareModule;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.wm.impl.IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN;
import static java.lang.Boolean.TRUE;

/**
 * Utility methods for {@link Project}s.
 */
public final class Projects {
  private static final Key<Boolean> SYNC_REQUESTED_DURING_BUILD = Key.create("project.sync.requested.during.build");
  private static final Key<Map<String, GradleVersion>> PLUGIN_VERSIONS_BY_MODULE = Key.create("project.plugin.versions.by.module");

  private Projects() {
  }

  @NotNull
  public static File getBaseDirPath(@NotNull Project project) {
    String basePath = project.getBasePath();
    assert basePath != null;
    return new File(toCanonicalPath(basePath));
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
    return androidFacet != null ? androidFacet.getAndroidModel() : null;
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
      AbstractProjectViewPane pane = projectView.getCurrentProjectViewPane();

      if (pane != null) {
        JComponent treeComponent = pane.getComponentToFocus();
        dataContext = DataManager.getInstance().getDataContext(treeComponent);
      }
      else {
        return Module.EMPTY_ARRAY;
      }
    }
    Module[] modules = MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modules != null) {
      if (modules.length == 1 && isProjectModule(modules[0])) {
        return ModuleManager.getInstance(project).getModules();
      }
      return modules;
    }
    Module module = MODULE.getData(dataContext);
    if (module != null) {
      return isProjectModule(module) ? ModuleManager.getInstance(project).getModules() : new Module[]{module};
    }
    return Module.EMPTY_ARRAY;
  }

  public static boolean isProjectModule(@NotNull Module module) {
    // if we got here is because we are dealing with a Gradle project, but if there is only one module selected and this module is the
    // module that corresponds to the project itself, it won't have an android-gradle facet. In this case we treat it as if we were going
    // to build the whole project.
    File moduleRootFolderPath = findModuleRootFolderPath(module);
    if (moduleRootFolderPath == null) {
      return false;
    }
    String basePath = module.getProject().getBasePath();
    return basePath != null && filesEqual(moduleRootFolderPath, new File(basePath)) && !GradleFacet.isAppliedTo(module);
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

  public static void storePluginVersionsPerModule(@NotNull Project project) {
    Map<String, GradleVersion> pluginVersionsPerModule = Maps.newHashMap();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model != null) {
        GradleFacet facet = GradleFacet.getInstance(module);
        if (facet != null) {
          GradleVersion modelVersion = model.getModelVersion();
          if (modelVersion != null) {
            pluginVersionsPerModule.put(facet.getConfiguration().GRADLE_PROJECT_PATH, modelVersion);
          }
        }
      }
    }

    project.putUserData(PLUGIN_VERSIONS_BY_MODULE, pluginVersionsPerModule);
  }

  @Nullable
  public static Map<String, GradleVersion> getPluginVersionsPerModule(@NotNull Project project) {
    return project.getUserData(PLUGIN_VERSIONS_BY_MODULE);
  }
}

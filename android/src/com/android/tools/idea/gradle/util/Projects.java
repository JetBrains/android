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

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.customizer.dependency.DependencySetupErrors;
import com.android.tools.idea.gradle.customizer.dependency.LibraryDependency;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.project.PostProjectSetupTasksExecutor;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
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
import java.util.Collection;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.*;
import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.wm.impl.IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static java.lang.Boolean.TRUE;

/**
 * Utility methods for {@link Project}s.
 */
public final class Projects {
  private static final Logger LOG = Logger.getInstance(Projects.class);

  private static final Key<String> GRADLE_VERSION = Key.create("project.gradle.version");
  private static final Key<LibraryDependency> MODULE_COMPILED_ARTIFACT = Key.create("module.compiled.artifact");
  private static final Key<Boolean> HAS_SYNC_ERRORS = Key.create("project.has.sync.errors");
  private static final Key<Boolean> HAS_WRONG_JDK = Key.create("project.has.wrong.jdk");
  private static final Key<DependencySetupErrors> DEPENDENCY_SETUP_ERRORS = Key.create("project.dependency.setup.errors");
  private static final Key<Collection<Module>> MODULES_TO_DISPOSE_POST_SYNC = Key.create("project.modules.to.dispose.post.sync");
  private static final Key<Boolean> SYNC_REQUESTED_DURING_BUILD = Key.create("project.sync.requested.during.build");

  private Projects() {
  }

  @NotNull
  public static File getBaseDirPath(@NotNull Project project) {
    String basePath = project.getBasePath();
    assert basePath != null;
    return new File(toCanonicalPath(basePath));
  }

  public static void setGradleVersionUsed(@NotNull Project project, @Nullable String gradleVersion) {
    project.putUserData(GRADLE_VERSION, gradleVersion);
  }

  @Nullable
  public static String getGradleVersionUsed(@NotNull Project project) {
    return project.getUserData(GRADLE_VERSION);
  }

  public static void removeAllModuleCompiledArtifacts(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      setModuleCompiledArtifact(module, null);
    }
  }

  public static void setModuleCompiledArtifact(@NotNull Module module, @Nullable LibraryDependency compiledArtifact) {
    module.putUserData(MODULE_COMPILED_ARTIFACT, compiledArtifact);
  }

  @Nullable
  public static LibraryDependency getModuleCompiledArtifact(@NotNull Module module) {
    return module.getUserData(MODULE_COMPILED_ARTIFACT);
  }

  public static void populate(@NotNull final Project project, @NotNull final Collection<DataNode<ModuleData>> modules) {
    invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
        messages.removeMessages(PROJECT_STRUCTURE_ISSUES, MISSING_DEPENDENCIES_BETWEEN_MODULES, FAILED_TO_SET_UP_DEPENDENCIES,
                                VARIANT_SELECTION_CONFLICTS, EXTRA_GENERATED_SOURCES);

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (!project.isDisposed()) {
              ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
                @Override
                public void run() {
                  ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);
                  dataManager.importData(modules, project, true /* synchronous */);
                }
              });
            }
          }
        });
        // We need to call this method here, otherwise the IDE will think the project is not a Gradle project and it won't generate
        // sources for it. This happens on new projects.
        PostProjectSetupTasksExecutor.getInstance(project).onProjectSyncCompletion();
      }
    });
  }

  public static void executeProjectChanges(@NotNull final Project project, @NotNull final Runnable changes) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      if (!project.isDisposed()) {
        changes.run();
      }
      return;
    }
    invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            if (!project.isDisposed()) {
              ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(changes);
            }
          }
        });
      }
    });
  }

  public static void setHasSyncErrors(@NotNull Project project, boolean hasSyncErrors) {
    project.putUserData(HAS_SYNC_ERRORS, hasSyncErrors);
  }

  public static void setHasWrongJdk(@NotNull Project project, boolean hasWrongJdk) {
    project.putUserData(HAS_WRONG_JDK, hasWrongJdk);
  }

  /**
   * Indicates whether the last sync with Gradle failed.
   */
  public static boolean lastGradleSyncFailed(@NotNull Project project) {
    return !GradleSyncState.getInstance(project).isSyncInProgress() && isBuildWithGradle(project) && requiredAndroidModelMissing(project);
  }

  public static boolean hasErrors(@NotNull Project project) {
    if (hasSyncErrors(project) || hasWrongJdk(project)) {
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

  private static boolean hasSyncErrors(@NotNull Project project) {
    return getBoolean(project, HAS_SYNC_ERRORS);
  }

  private static boolean hasWrongJdk(@NotNull Project project) {
    return getBoolean(project, HAS_WRONG_JDK);
  }

  private static boolean getBoolean(@NotNull Project project, @NotNull Key<Boolean> key) {
    Boolean val = project.getUserData(key);
    return val != null && val.booleanValue();
  }

  /**
   * Indicates the given project requires an Android model, but the model is {@code null}. Possible causes for this scenario to happen are:
   * <ul>
   * <li>the last sync with Gradle failed</li>
   * <li>Studio just started up and it has not synced the project yet</li>
   * </ul>
   *
   * @param project the project.
   * @return {@code true} if the project is a Gradle-based Android project that does not contain any Gradle model.
   */
  public static boolean requiredAndroidModelMissing(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.requiresAndroidModel() && facet.getAndroidModel() == null) {
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

  public static boolean isDirectGradleInvocationEnabled(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD;
  }

  public static boolean isOfflineBuildModeEnabled(@NotNull Project project) {
    return GradleSettings.getInstance(project).isOfflineWork();
  }

  /**
   * Indicates whether the given project has at least one module backed by an {@link AndroidProject}. To check if a project is a
   * "Gradle project," please use the method {@link Projects#isBuildWithGradle(Project)}.
   * @param project the given project.
   * @return {@code true} if the given project has one or more modules backed by an {@link AndroidProject}; {@code false} otherwise.
   */
  public static boolean requiresAndroidModel(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && androidFacet.requiresAndroidModel()) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static AndroidModel getAndroidModel(@NotNull Module module) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    return androidFacet != null ? androidFacet.getAndroidModel() : null;
  }

  /**
   * Indicates whether the give project is a legacy IDEA Android project (which is deprecated in Android Studio.)
   *
   * @param project the given project.
   * @return {@code true} if the given project is a legacy IDEA Android project; {@code false} otherwise.
   */
  public static boolean isLegacyIdeaAndroidProject(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.requiresAndroidModel()) {
        // If a module has the Android facet, but it does not require a model from the build system, it is a legacy IDEA project.
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
    if (requiresAndroidModel(project)) {
      // We only enforce JPS usage when the 'android' plug-in is not being used in Android Studio.
      if (!isAndroidStudio()) {
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
    File moduleFilePath = new File(toSystemDependentName(module.getModuleFilePath()));
    File moduleRootDirPath = moduleFilePath.getParentFile();
    if (moduleRootDirPath == null) {
      return false;
    }
    String basePath = module.getProject().getBasePath();
    return basePath != null && filesEqual(moduleRootDirPath, new File(basePath)) && !isBuildWithGradle(module);
  }

  /**
   * Indicates whether Gradle is used to build this project.
   * Note: {@link #requiresAndroidModel(Project)} indicates whether a project requires an {@link AndroidModel}.
   * That method should be preferred in almost all cases. Use this method only if you explicitly need to check whether the model is
   * Gradle-specific.
   */
  public static boolean isBuildWithGradle(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (isBuildWithGradle(module)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Indicates whether Gradle is used to build the module.
   */
  public static boolean isBuildWithGradle(@NotNull Module module) {
    return AndroidGradleFacet.getInstance(module) != null;
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
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet != null && androidFacet.requiresAndroidModel() && isBuildWithGradle(module)) {
      // If the module is an Android project, check that the module's path is the same as the project's.
      File moduleFilePath = new File(toSystemDependentName(module.getModuleFilePath()));
      File moduleRootDirPath = moduleFilePath.getParentFile();
      return pathsEqual(moduleRootDirPath.getPath(), module.getProject().getBasePath());
    }
    // For non-Android project modules, the top-level one is the one without an "Android-Gradle" facet.
    return !isBuildWithGradle(module);
  }

  @Nullable
  public static DependencySetupErrors getDependencySetupErrors(@NotNull Project project) {
    return project.getUserData(DEPENDENCY_SETUP_ERRORS);
  }

  public static void setDependencySetupErrors(@NotNull Project project, @Nullable DependencySetupErrors errors) {
    project.putUserData(DEPENDENCY_SETUP_ERRORS, errors);
  }

  @Nullable
  public static AndroidProject getAndroidModel(@NotNull VirtualFile file, @NotNull Project project) {
    Module module = findModuleForFile(file, project);
    if (module == null) {
      if (requiresAndroidModel(project)) {
        // You've edited a file that does not correspond to a module in a Gradle project; you are
        // most likely editing a file in an excluded folder under the build directory
        VirtualFile base = project.getBaseDir();
        VirtualFile parent = file.getParent();
        while (parent != null && parent.equals(base)) {
          module = findModuleForFile(parent, project);
          if (module != null) {
            break;
          }
          parent = parent.getParent();
        }
      }

      if (module == null) {
        return null;
      }
    }

    if (module.isDisposed()) {
      LOG.warn("Attempted to get an Android Facet from a disposed module");
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
      if (androidModel != null) {
        return androidModel.getAndroidProject();
      }
    }
    return null;
  }

  @Nullable
  public static Collection<Module> getModulesToDisposePostSync(@NotNull Project project) {
    return project.getUserData(MODULES_TO_DISPOSE_POST_SYNC);
  }

  public static void setModulesToDisposePostSync(@NotNull Project project, @Nullable Collection<Module> modules) {
    project.putUserData(MODULES_TO_DISPOSE_POST_SYNC, modules);
  }

  /**
   * Indicates whether the project in the given folder can be imported as a Gradle project.
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
    Boolean syncRequested = project.getUserData(SYNC_REQUESTED_DURING_BUILD);
    return syncRequested != null ? syncRequested : false;
  }
}

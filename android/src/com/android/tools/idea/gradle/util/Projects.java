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
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.subset.ProjectSubset;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.intellij.ide.impl.ProjectUtil.updateLastProjectLocation;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.PROJECT;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.wm.impl.IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN;
import static java.lang.Boolean.TRUE;

/**
 * Utility methods for {@link Project}s.
 */
public final class Projects {
  private static final Key<LibraryDependency> MODULE_COMPILED_ARTIFACT = Key.create("module.compiled.artifact");
  private static final Key<Collection<Module>> MODULES_TO_DISPOSE_POST_SYNC = Key.create("project.modules.to.dispose.post.sync");
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

  public static void populate(@NotNull Project project,
                              @NotNull DataNode<ProjectData> projectInfo,
                              @Nullable PostSyncProjectSetup.Request setupRequest,
                              boolean selectModulesToImport) {
    Collection<DataNode<ModuleData>> modulesToImport = getModulesToImport(project, projectInfo, selectModulesToImport);
    populate(project, projectInfo, modulesToImport, setupRequest);
  }

  @NotNull
  private static Collection<DataNode<ModuleData>> getModulesToImport(@NotNull Project project,
                                                                     @NotNull DataNode<ProjectData> projectInfo,
                                                                     boolean selectModulesToImport) {
    Collection<DataNode<ModuleData>> modules = findAll(projectInfo, ProjectKeys.MODULE);
    ProjectSubset subview = ProjectSubset.getInstance(project);
    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        ProjectSubset.getInstance(project).isFeatureEnabled() &&
        modules.size() > 1) {
      if (selectModulesToImport) {
        // Importing a project. Allow user to select which modules to include in the project.
        Collection<DataNode<ModuleData>> selection = subview.showModuleSelectionDialog(modules);
        if (selection != null) {
          return selection;
        }
      }
      else {
        // We got here because a project was synced with Gradle. Make sure that we don't add any modules that were not selected during
        // project import (if applicable.)
        String[] persistedModuleNames = subview.getSelection();
        if (persistedModuleNames != null) {
          int moduleCount = persistedModuleNames.length;
          if (moduleCount > 0) {
            List<String> moduleNames = Lists.newArrayList(persistedModuleNames);
            List<DataNode<ModuleData>> selectedModules = Lists.newArrayListWithExpectedSize(moduleCount);
            for (DataNode<ModuleData> module : modules) {
              String name = module.getData().getExternalName();
              if (moduleNames.contains(name)) {
                selectedModules.add(module);
              }
            }
            return selectedModules;
          }
        }
      }
    }
    // Delete any stored module selection.
    subview.clearSelection();
    return modules; // Import all modules, not just subset.
  }

  public static void populate(@NotNull Project project,
                              @NotNull DataNode<ProjectData> projectInfo,
                              @NotNull Collection<DataNode<ModuleData>> modulesToImport,
                              @Nullable PostSyncProjectSetup.Request setupRequest) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (project.isDisposed()) {
        return;
      }
      SyncMessages.getInstance(project).removeProjectMessages();
      disableExcludedModules(projectInfo, modulesToImport);
      doSelectiveImport(modulesToImport, project);

      // We need to call this method here, otherwise the IDE will think the project is not a Gradle project and it won't generate
      // sources for it. This happens on new projects.
      if (setupRequest != null) {
        PostSyncProjectSetup.getInstance(project).setUpProject(setupRequest, null);
      }
    });
  }

  /**
   * Reuse external system 'selective import' feature for importing of the project sub-set.
   */
  private static void disableExcludedModules(@NotNull DataNode<ProjectData> projectInfo,
                                             @NotNull Collection<DataNode<ModuleData>> selectedModules) {
    Collection<DataNode<ModuleData>> allModules = findAll(projectInfo, ProjectKeys.MODULE);
    if (selectedModules.size() != allModules.size()) {
      Set<DataNode<ModuleData>> moduleToIgnore = Sets.newHashSet(allModules);
      moduleToIgnore.removeAll(selectedModules);
      for (DataNode<ModuleData> moduleNode : moduleToIgnore) {
        visit(moduleNode, node -> node.setIgnored(true));
      }
    }
  }

  /**
   * Reuse external system 'selective import' feature for importing of the project sub-set.
   * And do not ignore projectNode children data, e.g. project libraries
   */
  private static void doSelectiveImport(@NotNull Collection<DataNode<ModuleData>> enabledModules, @NotNull Project project) {
    ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);
    DataNode<ProjectData> projectNode = enabledModules.isEmpty() ? null : findParent(enabledModules.iterator().next(), PROJECT);

    // do not ignore projectNode child data, e.g. project libraries
    if (projectNode != null) {
      final Collection<DataNode<ModuleData>> allModules = findAll(projectNode, ProjectKeys.MODULE);
      if (enabledModules.size() != allModules.size()) {
        final Set<DataNode<ModuleData>> moduleToIgnore = ContainerUtil.newIdentityTroveSet(allModules);
        moduleToIgnore.removeAll(enabledModules);
        for (DataNode<ModuleData> moduleNode : moduleToIgnore) {
          visit(moduleNode, node -> node.setIgnored(true));
        }
      }
      dataManager.importData(projectNode, project, true /* synchronous */);
    }
    else {
      dataManager.importData(enabledModules, project, true /* synchronous */);
    }
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
    }), ModalityState.defaultModalityState());
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
      if (isLegacyIdeaAndroidModule(module)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Indicates whether the given module is a legacy IDEA Android module (which is deprecated in Android Studio.)
   *
   * @param module the given module.
   * @return {@code true} if the given module is a legacy IDEA Android module; {@code false} otherwise.
   */
  public static boolean isLegacyIdeaAndroidModule(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && !facet.requiresAndroidModel()) {
      // If a module has the Android facet, but it does not require a model from the build system, it is a legacy IDEA project.
      return true;
    }
    return false;
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
   * Indicates whether Gradle is used to build the module.
   */
  // TODO move this method out of Projects.
  public static boolean isBuildWithGradle(@NotNull Module module) {
    return GradleFacet.getInstance(module) != null;
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
  public static Collection<Module> getModulesToDisposePostSync(@NotNull Project project) {
    return project.getUserData(MODULES_TO_DISPOSE_POST_SYNC);
  }

  public static void setModulesToDisposePostSync(@NotNull Project project, @Nullable Collection<Module> modules) {
    project.putUserData(MODULES_TO_DISPOSE_POST_SYNC, modules);
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
    return getBoolean(project, SYNC_REQUESTED_DURING_BUILD);
  }

  private static boolean getBoolean(@NotNull Project project, @NotNull Key<Boolean> key) {
    Boolean val = project.getUserData(key);
    return val != null && val.booleanValue();
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

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

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.google.common.base.Strings;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.options.ExternalBuildOptionListener;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for {@link Project}s.
 */
public final class Projects {
  private static final Key<BuildAction> PROJECT_BUILD_ACTION_KEY = Key.create("android.gradle.project.build.action");
  private static final Key<Boolean> GENERATE_SOURCE_ONLY_ON_COMPILE = Key.create("android.gradle.generate.source.only.on.compile");

  private static final Logger LOG = Logger.getInstance(Projects.class);

  private Projects() {
  }

  /**
   * Takes a project and compiles it, rebuilds it or simply generates source code based on the {@link BuildAction} set on the given project.
   * This method does nothing if the project does not have a {@link BuildAction}.
   *
   * @param project the given project.
   */
  public static void make(@NotNull Project project) {
    BuildAction buildAction = getBuildActionFrom(project);
    if (buildAction != null) {
      switch (buildAction) {
        case COMPILE:
          compile(project, project.getBasePath());
          break;
        case REBUILD:
          rebuild(project, project.getBasePath());
          break;
        case SOURCE_GEN:
          generateSourcesOnly(project, project.getBasePath());
          break;
      }
      removeBuildActionFrom(project);
    }
  }

  /**
   * Compiles the given project and refreshes the directory at the given path after compilation is finished. This method refreshes the
   * directory asynchronously and recursively.
   *
   * @param project          the given project.
   * @param dirToRefreshPath the path of the directory to refresh after compilation is finished.
   */
  public static void compile(@NotNull Project project, @NotNull String dirToRefreshPath) {
    CompilerManager.getInstance(project).make(new RefreshProjectAfterCompilation(dirToRefreshPath));
  }

  /**
   * Rebuilds the given project and refreshes the directory at the given path after compilation is finished. This method refreshes the
   * directory asynchronously and recursively. Rebuilding cleans the output directories and then compiles the project.
   *
   * @param project          the given project.
   * @param dirToRefreshPath the path of the directory to refresh after compilation is finished.
   */
  public static void rebuild(@NotNull Project project, @NotNull String dirToRefreshPath) {
    CompilerManager.getInstance(project).rebuild(new RefreshProjectAfterCompilation(dirToRefreshPath));
  }

  private static class RefreshProjectAfterCompilation implements CompileStatusNotification {
    @NotNull private final String myDirToRefreshPath;

    RefreshProjectAfterCompilation(@NotNull String dirToRefreshPath) {
      myDirToRefreshPath = dirToRefreshPath;
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(myDirToRefreshPath);
      if (rootDir != null && rootDir.isDirectory()) {
        rootDir.refresh(true, true);
      }
    }
  }

  /**
   * Generates source code instead of a full compilation. This method does nothing if the Gradle model does not specify the name of the
   * Gradle task to invoke.
   *
   * @param project the given project.
   * @param dirToRefreshPath the path of the directory to refresh after compilation is finished.
   */
  public static void generateSourcesOnly(@NotNull Project project, @NotNull String dirToRefreshPath) {
    if (hasSourceGenTasks(project)) {
      project.putUserData(GENERATE_SOURCE_ONLY_ON_COMPILE, true);
      compile(project, dirToRefreshPath);
    } else {
      String msg = String.format("Unable to find tasks for generating source code for project '%1$s'", project.getName());
      LOG.info(msg);
    }
  }

  private static boolean hasSourceGenTasks(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      AndroidFacet androidFacet = Facets.getFirstFacetOfType(module, AndroidFacet.ID);
      if (androidFacet != null) {
        String sourceGenTaskName = androidFacet.getConfiguration().getState().SOURCE_GEN_TASK_NAME;
        return !sourceGenTaskName.isEmpty() && !"TODO".equalsIgnoreCase(sourceGenTaskName);
      }
    }
    return false;
  }

  /**
   * Indicates whether the given project has at least one module that has the {@link AndroidGradleFacet}.
   *
   * @param project the given project.
   * @return {@code true} if the given project has at least one module that has the Android-Gradle facet, {@code false} otherwise.
   */
  public static boolean isGradleProject(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (Facets.getFirstFacetOfType(module, AndroidGradleFacet.TYPE_ID) != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the current Gradle project. This method must be called in the event dispatch thread.
   *
   * @return the current Gradle project, or {@code null} if the current project is not a Gradle one or if there are no projects open.
   */
  @Nullable
  public static Project getCurrentGradleProject() {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    boolean isGradleProject = project != null && isGradleProject(project);
    return isGradleProject ? project : null;
  }

  /**
   * Ensures that "External Build" is enabled for the given Gradle-based project. External build is the type of build that delegates project
   * building to Gradle.
   *
   * @param project the given project. This method does not do anything if the given project is not a Gradle-based project.
   */
  public static void ensureExternalBuildIsEnabledForGradleProject(@NotNull Project project) {
    if (isGradleProject(project)) {
      CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
      boolean wasUsingExternalMake = workspaceConfiguration.USE_COMPILE_SERVER;
      if (!wasUsingExternalMake) {
        String format = "Enabled 'External Build' for Android project '%1$s'. Otherwise, the project will not be built with Gradle";
        String msg = String.format(format, project.getName());
        LOG.info(msg);
        workspaceConfiguration.USE_COMPILE_SERVER = true;
        MessageBus messageBus = project.getMessageBus();
        messageBus.syncPublisher(ExternalBuildOptionListener.TOPIC).externalBuildOptionChanged(workspaceConfiguration.USE_COMPILE_SERVER);
      }
    }
  }

  public static void removeBuildActionFrom(@NotNull Project project) {
    setProjectBuildAction(project, null);
  }

  public static void setProjectBuildAction(@NotNull Project project, @Nullable BuildAction action) {
    project.putUserData(PROJECT_BUILD_ACTION_KEY, action);
  }

  @Nullable
  public static BuildAction getBuildActionFrom(@NotNull Project project) {
    return project.getUserData(PROJECT_BUILD_ACTION_KEY);
  }

  /**
   * Indicates whether the given project has the setting 'generate source code only'. Note that the setting is turned off after being
   * checked making subsequent calls to this method always return {@code false}.
   *
   * @param project the given project.
   * @return {@code true}
   */
  public static boolean generateSourceOnlyOnCompile(@NotNull Project project) {
    Boolean generateSourceCodeOnCompile = project.getUserData(GENERATE_SOURCE_ONLY_ON_COMPILE);
    project.putUserData(GENERATE_SOURCE_ONLY_ON_COMPILE, null);
    return generateSourceCodeOnCompile == Boolean.TRUE;
  }

  /**
   * Indicates whether a project should be built or not after a Gradle model refresh. "Building" means either compiling or rebuilding a
   * project.
   */
  public enum BuildAction {
    COMPILE, REBUILD, SOURCE_GEN
  }
}

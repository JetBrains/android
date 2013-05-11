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

import com.android.build.gradle.model.AndroidProject;
import com.android.build.gradle.model.Variant;
import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.model.AndroidContentRoot;
import com.android.tools.idea.gradle.model.AndroidContentRoot.ContentRootStorage;
import com.android.tools.idea.gradle.model.AndroidDependencies;
import com.android.tools.idea.gradle.model.AndroidDependencies.DependencyFactory;
import com.google.common.collect.Lists;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Imports a single Android-Gradle project into IDEA.
 */
class ProjectResolverStrategy {
  private static final Logger LOG = Logger.getInstance(ProjectResolverStrategy.class);

  @NotNull final GradleExecutionHelper myHelper;

  ProjectResolverStrategy(@NotNull GradleExecutionHelper helper) {
    myHelper = helper;
  }

  /**
   * Imports a single Android-Gradle project.
   *
   * @param id          id of the current 'resolve project info' task.
   * @param projectPath absolute path of the build.gradle file. It includes the file name.
   * @param settings    settings to use for the project resolving; {@code null} as indication that no specific settings are required.
   * @param connection  Gradle Tooling API connection to the project to import.
   * @return the imported project, or {@link null} if the project to import is not an Android-Gradle project.
   */
  @Nullable
  DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                           @NotNull String projectPath,
                                           @Nullable GradleExecutionSettings settings,
                                           @NotNull ProjectConnection connection) {
    AndroidProject androidProject = getAndroidProject(id, settings, connection);
    if (androidProject == null) {
      return null;
    }
    return createProjectInfo(androidProject, projectPath);
  }

  /**
   * Retrieves Android's Gradle model from the project to import.
   *
   * @param id         ID of the "import project" task.
   * @param settings   settings to use for the project resolving; {@code null} as indication that no specific settings are required.
   * @param connection connection to the Android/Gradle project to import.
   * @return the retrieved model, or {@code null} if the Gradle project to import is not an Android project.
   */
  @Nullable
  AndroidProject getAndroidProject(@NotNull ExternalSystemTaskId id,
                                   @Nullable GradleExecutionSettings settings,
                                   @NotNull ProjectConnection connection) {
    try {
      ModelBuilder<AndroidProject> modelBuilder = myHelper.getModelBuilder(AndroidProject.class, id, settings, connection);
      return modelBuilder.get();
    }
    catch (RuntimeException e) {
      handleProjectImportError(e);
    }
    return null;
  }

  private static void handleProjectImportError(@NotNull RuntimeException e) {
    if (e instanceof UnknownModelException) {
      return;
    }
    Throwable root = e;
    if (e instanceof BuildException) {
      root = ExceptionUtil.getRootCause(e);
    }
    LOG.error(root);
  }

  @NotNull
  private DataNode<ProjectData> createProjectInfo(@NotNull AndroidProject androidProject, @NotNull String projectPath) {
    String projectDirPath = PathUtil.getParentPath(projectPath);
    String name = androidProject.getName();

    DataNode<ProjectData> projectInfo = createProjectInfo(projectDirPath, projectPath, name);
    DataNode<ModuleData> moduleInfo = createModuleInfo(androidProject, name, projectInfo, projectDirPath);

    IdeaAndroidProject ideaAndroidProject = getIdeaAndroidProject(moduleInfo);
    if (ideaAndroidProject != null) {
      populateDependencies(projectInfo, moduleInfo, ideaAndroidProject);
    }

    return projectInfo;
  }
  @NotNull
  DataNode<ProjectData> createProjectInfo(@NotNull String projectDirPath, @NotNull String projectPath, @NotNull String name) {
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectDirPath, projectPath);
    projectData.setName(name);

    DataNode<ProjectData> projectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    // Gradle API doesn't expose project compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/build/classes");
    projectInfo.createChild(JavaProjectData.KEY, javaProjectData);

    return projectInfo;
  }

  @NotNull
  DataNode<ModuleData> createModuleInfo(@NotNull AndroidProject androidProject,
                                        @NotNull String name,
                                        @NotNull DataNode<ProjectData> projectInfo,
                                        @NotNull String moduleDirPath) {
    String projectDirPath = projectInfo.getData().getIdeProjectFileDirectoryPath();
    ModuleData moduleData = new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), name, projectDirPath);
    DataNode<ModuleData> moduleInfo = projectInfo.createChild(ProjectKeys.MODULE, moduleData);

    Variant selectedVariant = getFirstVariant(androidProject);
    IdeaAndroidProject ideaAndroidProject = new IdeaAndroidProject(moduleDirPath, androidProject, selectedVariant.getName());
    addContentRoot(ideaAndroidProject, moduleInfo, moduleDirPath);

    moduleInfo.createChild(AndroidProjectKeys.IDE_ANDROID_PROJECT, ideaAndroidProject);
    return moduleInfo;
  }

  private static void addContentRoot(@NotNull IdeaAndroidProject androidProject,
                                     @NotNull DataNode<ModuleData> moduleInfo,
                                     @NotNull String moduleDirPath) {
    final ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, moduleDirPath);
    ContentRootStorage storage = new ContentRootStorage() {
      @Override
      @NotNull
      public String getRootDirPath() {
        return contentRootData.getRootPath();
      }

      @Override
      public void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File directory) {
        contentRootData.storePath(sourceType, directory.getAbsolutePath());
      }
    };
    AndroidContentRoot.storePaths(androidProject, storage);
    moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, contentRootData);
  }

  @NotNull
  private static Variant getFirstVariant(@NotNull AndroidProject androidProject) {
    Map<String, Variant> variants = androidProject.getVariants();
    if (variants.size() == 1) {
      variants.values().iterator().next();
    }
    List<String> variantNames = Lists.newArrayList(variants.keySet());
    Collections.sort(variantNames);
    return variants.get(variantNames.get(0));
  }

  @Nullable
  static IdeaAndroidProject getIdeaAndroidProject(@NotNull DataNode<ModuleData> moduleInfo) {
    Collection<DataNode<IdeaAndroidProject>> projects =
      ExternalSystemApiUtil.getChildren(moduleInfo, AndroidProjectKeys.IDE_ANDROID_PROJECT);
    return getFirstNodeData(projects);
  }

  @Nullable
  static <T> T getFirstNodeData(Collection<DataNode<T>> nodes) {
    DataNode<T> node = ContainerUtil.getFirstItem(nodes);
    return node != null ? node.getData() : null;
  }

  void populateDependencies(@NotNull final DataNode<ProjectData> projectInfo,
                            @NotNull final DataNode<ModuleData> moduleInfo,
                            @NotNull IdeaAndroidProject ideaAndroidProject) {
    DependencyFactory dependencyFactory = new DependencyFactory() {
      @Override
      public void addDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull File binaryPath) {
        LibraryDependency dependency = new LibraryDependency(name);
        dependency.setScope(scope);
        dependency.addPath(LibraryPathType.BINARY, binaryPath);
        dependency.addTo(moduleInfo, projectInfo);
      }
    };
    AndroidDependencies.populate(ideaAndroidProject, dependencyFactory);
  }
}

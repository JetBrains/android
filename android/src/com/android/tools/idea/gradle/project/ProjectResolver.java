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
import com.android.tools.idea.gradle.IdeaGradleProject;
import com.android.tools.idea.gradle.model.AndroidContentRoot;
import com.android.tools.idea.gradle.model.AndroidDependencies;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Imports a Android-Gradle projects into IDEA. The set of projects to import may include regular Java projects as well.
 */
class ProjectResolver {
  private static final Logger LOG = Logger.getInstance(ProjectResolver.class);

  @NonNls private static final String GRADLE_PATH_SEPARATOR = ":";

  @NotNull final GradleExecutionHelper myHelper;

  ProjectResolver(@NotNull GradleExecutionHelper helper) {
    myHelper = helper;
  }

  /**
   * Imports multiple Android-Gradle projects. The set of projects to import may include regular Java projects as well.
   *
   * </p>Since the Android Gradle model does not support multiple projects, we query the Gradle Tooling API for a regular Java
   * multi-project. Then, for each of the modules in the imported project, we query for an (@link AndroidProject Android Gradle model.) If
   * we get one we create an IDE module from it, otherwise we just use the regular Java module. Unfortunately, this process requires
   * creation of multiple {@link ProjectConnection}s.
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
    String projectDirPath = PathUtil.getParentPath(projectPath);

    ModelBuilder<IdeaProject> modelBuilder = myHelper.getModelBuilder(IdeaProject.class, id, settings, connection);
    IdeaProject ideaProject = modelBuilder.get();
    if (ideaProject == null || ideaProject.getModules().isEmpty()) {
      return null;
    }

    String name = new File(projectDirPath).getName();
    DataNode<ProjectData> projectInfo = createProjectInfo(projectDirPath, projectPath, name);

    AndroidProject first = null;

    for (IdeaModule module : ideaProject.getModules()) {
      String moduleName = module.getName();
      IdeaGradleProject gradleProject = new IdeaGradleProject(moduleName, module.getGradleProject().getPath());
      String relativePath = getRelativePath(gradleProject);
      File moduleDir = new File(projectDirPath, relativePath);
      String gradleBuildFilePath = getGradleBuildFilePath(moduleDir);
      if (gradleBuildFilePath == null) {
        continue;
      }
      String moduleDirPath = moduleDir.getAbsolutePath();
      AndroidProject androidProject = getAndroidProject(id, gradleBuildFilePath, settings);
      if (androidProject != null) {
        createModuleInfo(androidProject, moduleName, projectInfo, moduleDirPath, gradleProject);
        if (first == null) {
          first = androidProject;
        }
        continue;
      }
      createModuleInfo(module, projectInfo, moduleDirPath, gradleProject);
    }

    if (first == null) {
      // Don't import project if we don't have at least one AndroidProject.
      return null;
    }

    populateDependencies(projectInfo);
    return projectInfo;
  }

  @NotNull
  private static DataNode<ProjectData> createProjectInfo(@NotNull String projectDirPath, @NotNull String projectPath, @NotNull String name) {
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectDirPath, projectPath);
    projectData.setName(name);

    DataNode<ProjectData> projectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    // Gradle API doesn't expose project compile output path yet.
    JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/build/classes");
    projectInfo.createChild(JavaProjectData.KEY, javaProjectData);

    return projectInfo;
  }

  @NotNull
  private static String getRelativePath(@NotNull IdeaGradleProject gradleProject) {
    String separator = File.separator;
    if (separator.equals("\\")) {
      separator = "\\\\";
    }
    String gradleProjectPath = gradleProject.getGradleProjectPath();
    return gradleProjectPath.replaceAll(GRADLE_PATH_SEPARATOR, separator);
  }

  @Nullable
  private static String getGradleBuildFilePath(@NotNull final File projectDir) {
    File[] children = projectDir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return "build.gradle".equals(name) && FileUtil.filesEqual(projectDir, dir);
      }
    });
    if (children != null && children.length == 1) {
      return children[0].getAbsolutePath();
    }
    return null;
  }

  @Nullable
  private AndroidProject getAndroidProject(@NotNull final ExternalSystemTaskId id,
                                           @NotNull String projectPath,
                                           @Nullable final GradleExecutionSettings settings) {
    return myHelper.execute(projectPath, settings, new Function<ProjectConnection, AndroidProject>() {
      @Nullable
      @Override
      public AndroidProject fun(ProjectConnection connection) {
        try {
          ModelBuilder<AndroidProject> modelBuilder = myHelper.getModelBuilder(AndroidProject.class, id, settings, connection);
          return modelBuilder.get();
        }
        catch (RuntimeException e) {
          handleProjectImportError(e);
        }
        return null;
      }
    });
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

  @NotNull
  private static DataNode<ModuleData> createModuleInfo(@NotNull AndroidProject androidProject,
                                                       @NotNull String moduleName,
                                                       @NotNull DataNode<ProjectData> projectInfo,
                                                       @NotNull String moduleDirPath,
                                                       @NotNull IdeaGradleProject gradleProject) {
    ModuleData moduleData = createModuleData(moduleName, moduleDirPath);
    DataNode<ModuleData> moduleInfo = projectInfo.createChild(ProjectKeys.MODULE, moduleData);

    Variant selectedVariant = getFirstVariant(androidProject);
    IdeaAndroidProject ideaAndroidProject =
      new IdeaAndroidProject(moduleName, moduleDirPath, androidProject, selectedVariant.getName());
    addContentRoot(ideaAndroidProject, moduleInfo, moduleDirPath);

    moduleInfo.createChild(AndroidProjectKeys.IDE_ANDROID_PROJECT, ideaAndroidProject);
    moduleInfo.createChild(AndroidProjectKeys.GRADLE_PROJECT, gradleProject);
    return moduleInfo;
  }

  @NotNull
  private static Variant getFirstVariant(@NotNull AndroidProject androidProject) {
    Map<String, Variant> variants = androidProject.getVariants();
    if (variants.size() == 1) {
      return ContainerUtil.getFirstItem(variants.values());
    }
    List<String> variantNames = Lists.newArrayList(variants.keySet());
    Collections.sort(variantNames);
    return variants.get(variantNames.get(0));
  }

  private static void addContentRoot(@NotNull IdeaAndroidProject androidProject,
                                     @NotNull DataNode<ModuleData> moduleInfo,
                                     @NotNull String moduleDirPath) {
    final ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, moduleDirPath);
    AndroidContentRoot.ContentRootStorage storage = new AndroidContentRoot.ContentRootStorage() {
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
  private static DataNode<ModuleData> createModuleInfo(@NotNull IdeaModule module,
                                                       @NotNull DataNode<ProjectData> projectInfo,
                                                       @NotNull String moduleDirPath,
                                                       @NotNull IdeaGradleProject gradleProject) {
    ModuleData moduleData = createModuleData(module.getName(), moduleDirPath);
    DataNode<ModuleData> moduleInfo = projectInfo.createChild(ProjectKeys.MODULE, moduleData);

    // Populate content roots.
    for (IdeaContentRoot from : module.getContentRoots()) {
      if (from == null || from.getRootDirectory() == null) {
        continue;
      }
      ContentRootData contentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, from.getRootDirectory().getAbsolutePath());
      GradleContentRoot.storePaths(from, contentRootData);
      moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, contentRootData);
    }

    moduleInfo.createChild(AndroidProjectKeys.IDEA_MODULE, module);
    moduleInfo.createChild(AndroidProjectKeys.GRADLE_PROJECT, gradleProject);
    return moduleInfo;
  }

  private static ModuleData createModuleData(String name, String dirPath) {
    return new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), name, dirPath);
  }

  private static void populateDependencies(@NotNull DataNode<ProjectData> projectInfo) {
    Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.getChildren(projectInfo, ProjectKeys.MODULE);
    for (DataNode<ModuleData> moduleInfo : modules) {
      IdeaAndroidProject androidProject = getIdeaAndroidProject(moduleInfo);
      if (androidProject != null) {
        populateDependencies(projectInfo, moduleInfo, androidProject);
        continue;
      }
      IdeaModule module = extractIdeaModule(moduleInfo);
      if (module != null) {
        GradleDependencies.populate(moduleInfo, projectInfo, module);
      }
    }
  }

  private static void populateDependencies(@NotNull final DataNode<ProjectData> projectInfo,
                                           @NotNull final DataNode<ModuleData> moduleInfo,
                                           @NotNull IdeaAndroidProject ideaAndroidProject) {
    AndroidDependencies.DependencyFactory dependencyFactory = new AndroidDependencies.DependencyFactory() {
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

  @Nullable
  private static IdeaModule extractIdeaModule(@NotNull DataNode<ModuleData> moduleInfo) {
    Collection<DataNode<IdeaModule>> modules = ExternalSystemApiUtil.getChildren(moduleInfo, AndroidProjectKeys.IDEA_MODULE);
    // it is safe to remove this node. We only needed it to resolve dependencies.
    moduleInfo.getChildren().removeAll(modules);
    return getFirstNodeData(modules);
  }
}

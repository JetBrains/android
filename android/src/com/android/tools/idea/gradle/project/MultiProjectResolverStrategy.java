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
import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collection;

/**
 * Imports a multiple Android-Gradle projects into IDEA. The set of projects to import may include regular Java projects as well.
 */
class MultiProjectResolverStrategy extends ProjectResolverStrategy {
  MultiProjectResolverStrategy(@NotNull GradleExecutionHelper helper) {
    super(helper);
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
  @Override
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
      GradleProject gradleProject = module.getGradleProject();
      String moduleDirPath = projectDirPath + File.separator + moduleName;
      File moduleDir = new File(moduleDirPath);
      String gradleBuildFilePath = getGradleBuildFilePath(moduleDir);
      if (gradleBuildFilePath == null) {
        continue;
      }
      AndroidProject androidProject = getAndroidProject(id, gradleBuildFilePath, settings);
      if (androidProject != null) {
        createModuleInfo(androidProject, moduleName, projectInfo, moduleDirPath, gradleProject);
        if (first == null) {
          first = androidProject;
        }
        continue;
      }
      createModuleInfo(module, projectInfo, gradleProject);
    }

    if (first == null) {
      // Don't import project if we don't have at least one AndroidProject.
      return null;
    }

    populateDependencies(projectInfo);
    return projectInfo;
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
        return getAndroidProject(id, settings, connection);
      }
    });
  }

  @NotNull
  private static DataNode<ModuleData> createModuleInfo(@NotNull IdeaModule module,
                                                       @NotNull DataNode<ProjectData> projectInfo,
                                                       @Nullable GradleProject gradleProject) {
    String projectDirPath = projectInfo.getData().getIdeProjectFileDirectoryPath();
    ModuleData moduleData = new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), module.getName(), projectDirPath);
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
    if (gradleProject != null) {
      moduleInfo.createChild(AndroidProjectKeys.GRADLE_PROJECT_KEY, gradleProject);
    }
    return moduleInfo;
  }

  private void populateDependencies(@NotNull DataNode<ProjectData> projectInfo) {
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

  @Nullable
  private static IdeaModule extractIdeaModule(@NotNull DataNode<ModuleData> moduleInfo) {
    Collection<DataNode<IdeaModule>> modules = ExternalSystemApiUtil.getChildren(moduleInfo, AndroidProjectKeys.IDEA_MODULE);
    // it is safe to remove this node. We only needed it to resolve dependencies.
    moduleInfo.getChildren().removeAll(modules);
    return getFirstNodeData(modules);
  }
}

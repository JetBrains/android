/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.plugins.gradle.util.GradleUtil.getConfigPath;

import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import one.util.streamex.StreamEx;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.project.data.BuildClasspathModuleGradleDataService;
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Set up build script classpath in Gradle local settings provider.
 * This is the counterpart of {@link BuildClasspathModuleGradleDataService} in IDEA sync.
 */
public class BuildScriptClasspathSetup {
  private static final Logger LOG = Logger.getInstance(BuildScriptClasspathSetup.class);

  public void setupBuildScriptClassPath(@NotNull SyncProjectModels projectModels,
                                        @NotNull Project project) {
    final String projectBaseDir = project.getBasePath();
    if (projectBaseDir == null) {
      LOG.warn("Project path is null, skip BuildScriptClasspathSetup.");
      return;
    }
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    if (manager == null) {
      LOG.warn("ExternalSystemManager is null, skip BuildScriptClasspathSetup.");
      return;
    }

    // Set GradleHomeDir, gradle home is the local directory where Gradle distribution was extracted.
    setGradleHomeDir(projectModels, project);

    AbstractExternalSystemLocalSettings<?> localSettings = manager.getLocalSettingsProvider().fun(project);
    final Map<String, ExternalProjectBuildClasspathPojo> localProjectBuildClasspath =
      new THashMap<>(localSettings.getProjectBuildClasspath());
    ExternalProjectBuildClasspathPojo projectBuildClasspathPojo = localProjectBuildClasspath.get(projectBaseDir);
    if (projectBuildClasspathPojo == null) {
      projectBuildClasspathPojo =
        new ExternalProjectBuildClasspathPojo(project.getName(), ContainerUtil.newArrayList(), ContainerUtil.newHashMap());
      localProjectBuildClasspath.put(projectBaseDir, projectBuildClasspathPojo);
    }

    // Set project build classpath.
    setProjectBuildClassPath(project, projectBuildClasspathPojo);

    // Set module build classpath.
    setModuleBuildClasspath(projectBaseDir, projectModels, projectBuildClasspathPojo);

    localSettings.setProjectBuildClasspath(localProjectBuildClasspath);

    if (!project.isDisposed()) {
      GradleBuildClasspathManager.getInstance(project).reload();
    }
  }

  private static void setProjectBuildClassPath(@NotNull Project project,
                                               @NotNull ExternalProjectBuildClasspathPojo projectBuildClasspathPojo) {
    final Set<String> gradleSdkLibraries = ContainerUtil.newLinkedHashSet();
    final String linkedExternalProjectPath = project.getBasePath();
    if (linkedExternalProjectPath != null) {
      final GradleInstallationManager gradleInstallationManager = ServiceManager.getService(GradleInstallationManager.class);
      File gradleHome = gradleInstallationManager.getGradleHome(project, linkedExternalProjectPath);
      if (gradleHome != null && gradleHome.isDirectory()) {
        final Collection<File> libraries = gradleInstallationManager.getClassRoots(project, linkedExternalProjectPath);
        if (libraries != null) {
          for (File library : libraries) {
            gradleSdkLibraries.add(toCanonicalPath(library.getPath()));
          }
        }
      }
    }
    projectBuildClasspathPojo.setProjectBuildClasspath(ContainerUtil.newArrayList(gradleSdkLibraries));
  }

  private static void setModuleBuildClasspath(@NotNull String projectBaseDir,
                                              @NotNull SyncProjectModels projectModels,
                                              @NotNull ExternalProjectBuildClasspathPojo projectBuildClasspathPojo) {
    for (SyncModuleModels moduleModels : projectModels.getModuleModels()) {
      final BuildScriptClasspathModel buildScriptClasspathModel = moduleModels.findModel(BuildScriptClasspathModel.class);
      final List<BuildScriptClasspathData.ClasspathEntry> classpathEntries;
      if (buildScriptClasspathModel == null) {
        classpathEntries = ContainerUtil.emptyList();
      }
      else {
        classpathEntries = ContainerUtil.map(
          buildScriptClasspathModel.getClasspath(),
          (Function<ClasspathEntryModel, BuildScriptClasspathData.ClasspathEntry>)model -> new BuildScriptClasspathData.ClasspathEntry(
            model.getClasses(), model.getSources(), model.getJavadoc()));
      }

      final Set<String> buildClasspathSources = ContainerUtil.newLinkedHashSet();
      final Set<String> buildClasspathClasses = ContainerUtil.newLinkedHashSet();
      for (BuildScriptClasspathData.ClasspathEntry classpathEntry : classpathEntries) {
        buildClasspathSources.addAll(classpathEntry.getSourcesFile().stream().map(entry -> toCanonicalPath(entry)).collect(toList()));
        buildClasspathClasses.addAll(classpathEntry.getClassesFile().stream().map(entry -> toCanonicalPath(entry)).collect(toList()));
      }
      List<String> buildClasspath = StreamEx.of(buildClasspathSources).append(buildClasspathClasses).collect(toList());
      String externalModulePath = getModulePath(projectBaseDir, moduleModels);
      projectBuildClasspathPojo.getModulesBuildClasspath()
        .put(externalModulePath, new ExternalModuleBuildClasspathPojo(externalModulePath, buildClasspath));
    }
  }

  @NotNull
  private static String getModulePath(@NotNull String projectBaseDir, @NotNull GradleModuleModels moduleModels) {
    final GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
    assert gradleProject != null;
    try {
      return ExternalSystemApiUtil.toCanonicalPath(gradleProject.getProjectDirectory().getCanonicalPath());
    }
    catch (IOException e) {
      return getConfigPath(gradleProject, projectBaseDir);
    }
  }

  private static void setGradleHomeDir(@NotNull SyncProjectModels projectModels,
                                       @NotNull Project project) {
    final String linkedExternalProjectPath = project.getBasePath();
    if (linkedExternalProjectPath != null) {
      for (SyncModuleModels moduleModels : projectModels.getModuleModels()) {
        final BuildScriptClasspathModel buildScriptClasspathModel = moduleModels.findModel(BuildScriptClasspathModel.class);
        if (buildScriptClasspathModel != null) {
          final File gradleHomeDir = buildScriptClasspathModel.getGradleHomeDir();
          if (gradleHomeDir != null) {
            GradleLocalSettings.getInstance(project).setGradleHome(linkedExternalProjectPath, gradleHomeDir.getPath());
            return;
          }
        }
      }
    }
  }
}

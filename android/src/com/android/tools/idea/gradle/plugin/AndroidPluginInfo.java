/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.plugin;

import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.CLASSPATH;
import static com.intellij.openapi.module.ModuleUtilCore.findModuleForFile;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.annotations.concurrency.Slow;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.BuildFileProcessor;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidPluginInfo {
  public static final String APPLICATION_PLUGIN_ID = "com.android.application";
  public static final String DESCRIPTION = "Android Gradle plugin";
  public static final String ARTIFACT_ID = "gradle";
  public static final String API_ARTIFACT_ID = "gradle-api";
  public static final String GROUP_ID = "com.android.tools.build";

  @NotNull private final Module myModule;
  @Nullable private final GradleVersion myPluginVersion; // May not be present if plugin dependency can not be located
  @Nullable private final VirtualFile myPluginBuildFile; // May not be present if plugin dependency can not be located

  /**
   * Attempts to obtain information about the Android plugin from the project's "application" module in the given project.
   *
   * @param project the given project.
   * @return the Android plugin information, if the "application" module was found; {@code null} otherwise.
   */
  @Slow
  @Nullable
  public static AndroidPluginInfo find(@NotNull Project project) {
    AndroidPluginInfo modelPluginInfo = findFromModel(project);
    if (modelPluginInfo != null && modelPluginInfo.getPluginVersion() != null) {
      return modelPluginInfo;
    }


    AndroidPluginInfo buildPluginInfo = findInBuildFiles(project, modelPluginInfo == null ? null : modelPluginInfo.getModule());
    return buildPluginInfo == null ? modelPluginInfo : buildPluginInfo;
  }

  /**
   * Attempts to obtain information about the Android plugin from the project's "application" module in the given project.
   * <p/>
   * This method ignores any {@code AndroidProject}s and reads the plugin's information from build.gradle files.
   *
   * @param project the given project.
   * @return the Android plugin information, if the "application" module was found; {@code null} otherwise.
   */
  @Slow
  @Nullable
  public static AndroidPluginInfo findFromBuildFiles(@NotNull Project project) {
    return findInBuildFiles(project, null);
  }

  @Nullable
  public static AndroidPluginInfo findFromModel(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidModuleModel gradleModel = AndroidModuleModel.get(module);
      if (gradleModel != null && gradleModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP) {
        // This is the 'app' module in the project.
        return new AndroidPluginInfo(module, gradleModel.getModelVersion(), null);
      }
    }
    return null;
  }

  @Slow
  @Nullable
  private static AndroidPluginInfo findInBuildFiles(@NotNull Project project, @Nullable Module appModule) {
    Module fileAppModule = null;
    // Try to find 'app' module or plugin version by reading build.gradle files.
    BuildFileSearchResult result = searchInBuildFiles(project, appModule == null);
    if (result.appVirtualFile != null) {
      fileAppModule = findModuleForFile(result.appVirtualFile, project);
    }
    if (fileAppModule != null || appModule != null) {
      GradleVersion pluginVersion = isNotEmpty(result.pluginVersion) ? GradleVersion.tryParse(result.pluginVersion) : null;
      return new AndroidPluginInfo(fileAppModule == null ? appModule : fileAppModule, pluginVersion, result.pluginVirtualFile);
    }
    return null;
  }

  @Slow
  @NotNull
  private static BuildFileSearchResult searchInBuildFiles(@NotNull Project project,
                                                          boolean searchForAppModule) {
    BuildFileSearchResult result = new BuildFileSearchResult();

    BuildFileProcessor.getInstance().processRecursively(project, buildModel -> {
      boolean keepSearchingForAppModule = searchForAppModule && result.appVirtualFile == null;
      if (keepSearchingForAppModule) {
        List<String> pluginIds = PluginModel.extractNames(buildModel.plugins());
        if (pluginIds.contains(APPLICATION_PLUGIN_ID)) {
          result.appVirtualFile = buildModel.getVirtualFile();
          keepSearchingForAppModule = false;
        }
      }

      boolean keepSearchingForPluginVersion = result.pluginVersion == null;
      if (keepSearchingForPluginVersion) {
        DependenciesModel dependencies = buildModel.buildscript().dependencies();
        for (ArtifactDependencyModel dependency : dependencies.artifacts(CLASSPATH)) {
          if (isAndroidPlugin(dependency.name().forceString(), dependency.group().toString())) {
            String version = dependency.version().toString();
            if (isNotEmpty(version)) {
              result.pluginVirtualFile = buildModel.getVirtualFile();
              result.pluginVersion = version;
            }
            keepSearchingForPluginVersion = false;
            break;
          }
        }
      }
      return keepSearchingForAppModule || keepSearchingForPluginVersion;
    });

    return result;
  }

  @VisibleForTesting
  public AndroidPluginInfo(@NotNull Module module,
                           @Nullable GradleVersion pluginVersion,
                           @Nullable VirtualFile pluginBuildFile) {
    myModule = module;
    myPluginVersion = pluginVersion;
    myPluginBuildFile = pluginBuildFile;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  // Provides singleton mock support in tests
  @NotNull
  public LatestKnownPluginVersionProvider getLatestKnownPluginVersionProvider() {
    return LatestKnownPluginVersionProvider.INSTANCE;
  }

  @Nullable
  public GradleVersion getPluginVersion() {
    return myPluginVersion;
  }

  @Nullable
  public VirtualFile getPluginBuildFile() {
    return myPluginBuildFile;
  }

  public boolean isExperimental() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AndroidPluginInfo that = (AndroidPluginInfo)o;
    return Objects.equals(myModule, that.myModule) &&
           Objects.equals(myPluginVersion, that.myPluginVersion) &&
           Objects.equals(myPluginBuildFile, that.myPluginBuildFile);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myModule, myPluginVersion, myPluginBuildFile);
  }

  public static boolean isAndroidPlugin(@NotNull String artifactId, @com.android.annotations.Nullable String groupId) {
    return ARTIFACT_ID.equals(artifactId) && GROUP_ID.equals(groupId);
  }

  public static boolean isAndroidPluginOrApi(@NotNull String artifactId, @Nullable String groupId) {
    return isAndroidPlugin(artifactId, groupId) || API_ARTIFACT_ID.equals(artifactId) && GROUP_ID.equals(groupId);
  }

  private static class BuildFileSearchResult {
    @Nullable VirtualFile appVirtualFile;
    @Nullable VirtualFile pluginVirtualFile;
    @Nullable String pluginVersion;
  }
}

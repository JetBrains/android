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
package com.android.tools.idea.gradle.project.sync.idea;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.model.JavaModuleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.project.facet.gradle.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaGradleFacet;
import com.android.tools.idea.gradle.project.facet.cpp.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.project.GradleProjectSyncData;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.setup.project.idea.PostSyncProjectSetup;
import com.android.tools.idea.gradle.project.sync.GradleSync;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.*;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getCachedProjectData;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.util.ProxyUtil.isValidProxyObject;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshProject;
import static org.jetbrains.android.AndroidPlugin.getGuiTestSuiteState;
import static org.jetbrains.android.AndroidPlugin.isGuiTestingMode;

@Deprecated
public class IdeaGradleSync implements GradleSync {
  private static final boolean SYNC_WITH_CACHED_MODEL_ONLY =
    SystemProperties.getBooleanProperty("studio.sync.with.cached.model.only", false);

  @Override
  public void sync(@NotNull Project project,
                   @NotNull GradleSyncInvoker.Request request,
                   @Nullable GradleSyncListener listener) {
    // Prevent IDEA from syncing with Gradle. We want to have full control of syncing.
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true);

    if (forceSyncWithCachedModel() || request.isUseCachedGradleModels()) {
      GradleProjectSyncData syncData = GradleProjectSyncData.getInstance((project));
      if (syncData != null && syncData.canUseCachedProjectData()) {
        DataNode<ProjectData> cache = getCachedProjectData(project);
        if (cache != null && !isCacheMissingModels(cache, project)) {
          PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();

          // @formatter:off
          setupRequest.setUsingCachedGradleModels(true)
                      .setGenerateSourcesAfterSync(false)
                      .setLastSyncTimestamp(syncData.getLastGradleSyncTimestamp());
          // @formatter:on

          boolean newProject = request.isNewProject();
          ProjectSetUpTask setUpTask = new ProjectSetUpTask(project, setupRequest, listener, newProject, !newProject, true);
          setUpTask.onSuccess(cache);
          return;
        }
      }
    }

    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();

    // @formatter:off
    setupRequest.setGenerateSourcesAfterSync(request.isGenerateSourcesOnSuccess())
                .setCleanProjectAfterSync(request.isCleanProject());
    // @formatter:on

    String externalProjectPath = getBaseDirPath(project).getPath();

    ProjectSetUpTask setUpTask =
      new ProjectSetUpTask(project, setupRequest, listener, request.isNewProject(), false, false);
    ProgressExecutionMode executionMode = request.getProgressExecutionMode();
    refreshProject(project, GRADLE_SYSTEM_ID, externalProjectPath, setUpTask, false /* resolve dependencies */,
                   executionMode, true /* always report import errors */);
  }

  private static boolean forceSyncWithCachedModel() {
    if (SYNC_WITH_CACHED_MODEL_ONLY) {
      return true;
    }
    if (isGuiTestingMode()) {
      AndroidPlugin.GuiTestSuiteState state = getGuiTestSuiteState();
      return state.syncWithCachedModelOnly();
    }
    return false;
  }

  @VisibleForTesting
  static boolean isCacheMissingModels(@NotNull DataNode<ProjectData> cache, @NotNull Project project) {
    Collection<DataNode<ModuleData>> moduleDataNodes = findAll(cache, MODULE);
    if (!moduleDataNodes.isEmpty()) {
      Map<String, DataNode<ModuleData>> moduleDataNodesByName = indexByModuleName(moduleDataNodes);

      ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        DataNode<ModuleData> moduleDataNode = moduleDataNodesByName.get(module.getName());
        if (moduleDataNode == null) {
          // When a Gradle facet is present, there should be a cache node for the module.
          AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
          if (gradleFacet != null) {
            return true;
          }
        }
        else if (isCacheMissingModels(moduleDataNode, module)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  @NotNull
  private static Map<String, DataNode<ModuleData>> indexByModuleName(@NotNull Collection<DataNode<ModuleData>> moduleDataNodes) {
    Map<String, DataNode<ModuleData>> mapping = Maps.newHashMap();
    for (DataNode<ModuleData> moduleDataNode : moduleDataNodes) {
      ModuleData data = moduleDataNode.getData();
      mapping.put(data.getExternalName(), moduleDataNode);
    }
    return mapping;
  }

  private static boolean isCacheMissingModels(@NotNull DataNode<ModuleData> cache, @NotNull Module module) {
    AndroidGradleFacet gradleFacet = AndroidGradleFacet.getInstance(module);
    if (gradleFacet != null) {
      DataNode<GradleModuleModel> gradleDataNode = find(cache, GRADLE_MODULE_MODEL);
      if (gradleDataNode == null) {
        return true;
      }

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        DataNode<AndroidGradleModel> androidDataNode = find(cache, ANDROID_MODEL);
        if (androidDataNode == null || !isValidProxyObject(androidDataNode.getData().getAndroidProject())) {
          return true;
        }
      }
      else {
        JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
        if (javaFacet != null) {
          DataNode<JavaModuleModel> javaProjectDataNode = find(cache, JAVA_MODULE_MODEL);
          if (javaProjectDataNode == null) {
            return true;
          }
        }
      }
    }
    NativeAndroidGradleFacet nativeAndroidFacet = NativeAndroidGradleFacet.getInstance(module);
    if (nativeAndroidFacet != null) {
      DataNode<NativeAndroidGradleModel> nativeAndroidGradleDataNode = find(cache, NATIVE_ANDROID_MODEL);
      if (nativeAndroidGradleDataNode == null || !isValidProxyObject(nativeAndroidGradleDataNode.getData().getNativeAndroidProject())) {
        return true;
      }
    }
    return false;
  }
}

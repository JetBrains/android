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
package com.android.tools.idea.gradle.project.sync.idea.data;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.*;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;

public class DataNodeCaches {
  @NotNull private final Project myProject;

  @NotNull
  public static DataNodeCaches getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DataNodeCaches.class);
  }

  public DataNodeCaches(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  public DataNode<ProjectData> getCachedProjectData() {
    ProjectDataManager dataManager = ProjectDataManager.getInstance();
    String projectPath = getBaseDirPath(myProject).getPath();
    ExternalProjectInfo projectInfo = dataManager.getExternalProjectData(myProject, GradleConstants.SYSTEM_ID, projectPath);
    return projectInfo != null ? projectInfo.getExternalProjectStructure() : null;
  }

  public boolean isCacheMissingModels(@NotNull DataNode<ProjectData> cache) {
    Collection<DataNode<ModuleData>> moduleDataNodes = findAll(cache, MODULE);
    if (!moduleDataNodes.isEmpty()) {
      Map<String, DataNode<ModuleData>> moduleDataNodesByName = indexByModuleName(moduleDataNodes);

      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      for (Module module : moduleManager.getModules()) {
        DataNode<ModuleData> moduleDataNode = moduleDataNodesByName.get(module.getName());
        if (moduleDataNode == null) {
          // When a Gradle facet is present, there should be a cache node for the module.
          GradleFacet gradleFacet = GradleFacet.getInstance(module);
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
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet != null) {
      DataNode<GradleModuleModel> gradleDataNode = find(cache, GRADLE_MODULE_MODEL);
      if (gradleDataNode == null) {
        return true;
      }

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        DataNode<AndroidModuleModel> androidDataNode = find(cache, ANDROID_MODEL);
        if (androidDataNode == null) {
          return true;
        }
      }
      else {
        JavaFacet javaFacet = JavaFacet.getInstance(module);
        if (javaFacet != null) {
          DataNode<JavaModuleModel> javaProjectDataNode = find(cache, JAVA_MODULE_MODEL);
          if (javaProjectDataNode == null) {
            return true;
          }
        }
      }
    }
    NdkFacet ndkFacet = NdkFacet.getInstance(module);
    if (ndkFacet != null) {
      DataNode<NdkModuleModel> ndkModuleModelDataNode = find(cache, NDK_MODEL);
      if (ndkModuleModelDataNode == null) {
        return true;
      }
    }
    return false;
  }
}

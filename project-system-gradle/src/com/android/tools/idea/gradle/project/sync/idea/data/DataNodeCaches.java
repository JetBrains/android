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

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.NDK_MODEL;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAllRecursively;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManagerImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class DataNodeCaches {
  @NotNull private final Project myProject;

  @NotNull
  public static DataNodeCaches getInstance(@NotNull Project project) {
    return project.getService(DataNodeCaches.class);
  }

  public DataNodeCaches(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  public DataNode<ProjectData> getCachedProjectData() {
    ExternalProjectInfo projectInfo = getExternalProjectInfo();
    return projectInfo != null ? projectInfo.getExternalProjectStructure() : null;
  }

  @Nullable
  private ExternalProjectInfo getExternalProjectInfo() {
    ProjectDataManager dataManager = ProjectDataManager.getInstance();
    String projectPath = getBaseDirPath(myProject).getPath();
    return dataManager.getExternalProjectData(myProject, GradleConstants.SYSTEM_ID, projectPath);
  }

  public void clearCaches() {
    ExternalProjectInfo projectInfo = getExternalProjectInfo();
    if (projectInfo == null) {
      return;
    }
    DataNode<ProjectData> cache = projectInfo.getExternalProjectStructure();
    if (cache == null) {
      return;
    }
    clearCaches(cache);
    // Call updateExternalProjectData to trigger refresh of DataNode, then save project data to disk.
    ProjectDataManagerImpl.getInstance().updateExternalProjectData(myProject, projectInfo);
    myProject.save();
  }

  private static void clearCaches(@NotNull DataNode<ProjectData> cache) {
    clearCachesOfType(cache, GRADLE_MODULE_MODEL);
    clearCachesOfType(cache, ANDROID_MODEL);
    clearCachesOfType(cache, JAVA_MODULE_MODEL);
    clearCachesOfType(cache, NDK_MODEL);
  }

  private static <T> void clearCachesOfType(@NotNull DataNode<ProjectData> cache, @NotNull Key<T> type) {
    for (DataNode<T> dataNode : findAllRecursively(cache, type)) {
      dataNode.clear(true);
    }
  }
}

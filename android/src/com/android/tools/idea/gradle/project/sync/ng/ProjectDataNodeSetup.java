/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.TASK;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.isIdeaTask;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;
import static org.jetbrains.plugins.gradle.util.GradleUtil.getConfigPath;

public class ProjectDataNodeSetup {
  /**
   * Creates ProjectData DataNode for the the given project.
   *
   * @param projectModels the project models returned by Gradle.
   * @param project       the root project to create ProjectData DataNode for.
   */
  public void setupProjectDataNode(@NotNull SyncProjectModels projectModels, @NotNull Project project) {
    String projectFolder = project.getBasePath();
    assert projectFolder != null;
    ProjectData projectData = new ProjectData(SYSTEM_ID, project.getName(), projectFolder, projectFolder);
    DataNode<ProjectData> projectDataNode = new DataNode<>(ProjectKeys.PROJECT, projectData, null);
    for (GradleModuleModels moduleModels : projectModels.getSyncModuleModels()) {
      DataNode<ModuleData> moduleData = createModuleDataNode(moduleModels, projectDataNode);
      createTaskDataNode(moduleModels, moduleData);
    }
    // Link to external project.
    InternalExternalProjectInfo projectInfo = new InternalExternalProjectInfo(SYSTEM_ID, projectFolder, projectDataNode);
    //noinspection deprecation
    ProjectDataManager.getInstance().updateExternalProjectData(project, projectInfo);
  }

  @NotNull
  private static DataNode<ModuleData> createModuleDataNode(@NotNull GradleModuleModels moduleModels,
                                                           @NotNull DataNode<ProjectData> projectDataNode) {
    GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
    assert gradleProject != null;

    String moduleConfigPath;
    try {
      moduleConfigPath = toCanonicalPath(gradleProject.getProjectDirectory().getCanonicalPath());
    }
    catch (IOException e) {
      moduleConfigPath = getConfigPath(gradleProject, projectDataNode.getData().getLinkedExternalProjectPath());
    }

    String moduleName = moduleModels.getModuleName();
    String gradlePath = gradleProject.getPath();
    String moduleId = isEmpty(gradlePath) || ":".equals(gradlePath) ? moduleName : gradlePath;
    String typeId = StdModuleTypes.JAVA.getId();
    ModuleData moduleData = new ModuleData(moduleId, SYSTEM_ID, typeId, moduleName, moduleConfigPath, moduleConfigPath);
    moduleData.setDescription(gradleProject.getDescription());
    return projectDataNode.createChild(MODULE, moduleData);
  }

  private static void createTaskDataNode(@NotNull GradleModuleModels moduleModels,
                                         @NotNull DataNode<ModuleData> moduleData) {
    GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
    assert gradleProject != null;
    for (GradleTask task : gradleProject.getTasks()) {
      String taskName = task.getName();
      String taskGroup;
      try {
        taskGroup = task.getGroup();
      }
      catch (UnsupportedMethodException e) {
        taskGroup = null;
      }
      if (taskName == null || taskName.trim().isEmpty() || isIdeaTask(taskName, taskGroup)) {
        continue;
      }
      TaskData taskData = new TaskData(SYSTEM_ID, taskName, gradleProject.getPath(), task.getDescription());
      taskData.setGroup(taskGroup);
      moduleData.createChild(TASK, taskData);
    }
  }
}

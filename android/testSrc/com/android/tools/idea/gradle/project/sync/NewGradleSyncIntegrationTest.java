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
package com.android.tools.idea.gradle.project.sync;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.stream.Collectors;

import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.TASK;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

/**
 * Integration tests for 'Gradle Sync' using the new Sync infrastructure.
 */
public class NewGradleSyncIntegrationTest extends GradleSyncIntegrationTest {
  @Override
  protected boolean useNewSyncInfrastructure() {
    return true;
  }

  public void testTaskViewPopulated() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    verifyTaskViewPopulated(getProject());
  }

  static void verifyTaskViewPopulated(@NotNull Project project) {
    // Verify that external project is linked for current project.
    Collection<ExternalProjectInfo> projectInfos = ProjectDataManager.getInstance().getExternalProjectsData(project, SYSTEM_ID);
    assertThat(projectInfos).hasSize(1);

    // Verify ProjectData DataNode is not null.
    ExternalProjectInfo projectInfo = projectInfos.iterator().next();
    DataNode<ProjectData> projectDataNode = projectInfo.getExternalProjectStructure();
    assertNotNull(projectDataNode);

    // Verify ModuleData DataNode is created for each of these modules,
    // RootModule, :app, :library1, :library2, :lib
    Collection<DataNode<ModuleData>> moduleNodes = findAll(projectDataNode, MODULE);
    Collection<String> moduleNames = moduleNodes.stream().map(moduleNode -> moduleNode.getData().getId()).collect(Collectors.toList());
    assertThat(moduleNames).containsExactly(project.getName(), ":app", ":library1", ":library2", ":lib");

    // Verify that each ModuleData contains TASK node.
    for (DataNode<ModuleData> moduleData : moduleNodes) {
      Collection<DataNode<TaskData>> tasks = findAll(moduleData, TASK);
      assertThat(tasks).isNotEmpty();
    }
  }
}

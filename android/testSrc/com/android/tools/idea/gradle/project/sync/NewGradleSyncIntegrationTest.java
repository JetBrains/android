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

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.TestProjectPaths.JAVA_LIB;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.TASK;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

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

  public void testUnitTestClassPath() throws Exception {
    prepareProjectForImport(JAVA_LIB);
    ProjectBuildModel pbm = ProjectBuildModel.get(getProject());
    GradleBuildModel gbm = pbm.getModuleBuildModel(new File(getProjectFolderPath(), "lib"));
    gbm.dependencies().addArtifact("testImplementation", "org.mockito:mockito-core:2.7.1");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> pbm.applyChanges());

    importProject();

    Module module = myModules.getAppModule();
    assertNotNull(module);

    List<String> dependencies =
      OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathList().stream().map(path -> new File(path).getName())
        .collect(Collectors.toList());
    assertThat(dependencies)
      .containsExactly("android.jar", "res", "junit-4.12.jar", "mockito-core-2.7.1.jar", "hamcrest-core-1.3.jar", "byte-buddy-1.6.5.jar",
                       "byte-buddy-agent-1.6.5.jar", "objenesis-2.5.jar");
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
    // RootModule, :app, :library1, :library2, :javalib1, :javalib2
    Collection<DataNode<ModuleData>> moduleNodes = findAll(projectDataNode, MODULE);
    Collection<String> moduleNames = moduleNodes.stream().map(moduleNode -> moduleNode.getData().getId()).collect(Collectors.toList());
    assertThat(moduleNames).containsExactly(project.getName(), ":app", ":library1", ":library2", ":javalib1", ":javalib2");

    // Verify that each ModuleData contains TASK node.
    for (DataNode<ModuleData> moduleData : moduleNodes) {
      Collection<DataNode<TaskData>> tasks = findAll(moduleData, TASK);
      assertThat(tasks).isNotEmpty();
    }
  }
}

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
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProjectDataNodeSetup}.
 */
public class ProjectDataNodeSetupTest extends IdeaTestCase {
  @Mock private SyncProjectModels mySyncProjectModels;
  private ProjectDataNodeSetup mySetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySetup = new ProjectDataNodeSetup();
  }

  public void testSetupProjectDataNode() throws Exception {
    mySetup.setupProjectDataNode(mySyncProjectModels, myProject);

    // Verify that external project is linked for current project.
    Collection<ExternalProjectInfo> projectInfos = ProjectDataManager.getInstance().getExternalProjectsData(myProject, SYSTEM_ID);
    assertThat(projectInfos).hasSize(1);

    // Verify ProjectData DataNode is not null.
    ExternalProjectInfo projectInfo = projectInfos.iterator().next();
    DataNode<ProjectData> projectDataNode = projectInfo.getExternalProjectStructure();
    assertNotNull(projectDataNode);
  }
}

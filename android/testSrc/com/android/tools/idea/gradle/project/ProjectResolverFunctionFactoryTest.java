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

import com.android.tools.idea.gradle.project.AndroidGradleProjectResolver.ProjectResolverFunctionFactory;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.util.Function;
import junit.framework.TestCase;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link ProjectResolverFunctionFactory}.
 */
public class ProjectResolverFunctionFactoryTest extends TestCase {
  private ProjectResolverStrategy myStrategy1;
  private ProjectResolverStrategy myStrategy2;
  private ProjectResolverFunctionFactory myFunctionFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStrategy1 = createMock(ProjectResolverStrategy.class);
    myStrategy2 = createMock(ProjectResolverStrategy.class);
    myFunctionFactory = new ProjectResolverFunctionFactory(myStrategy1, myStrategy2);
  }

  public void testCreateFunction() {
    ExternalSystemTaskId id = ExternalSystemTaskId.create(ExternalSystemTaskType.RESOLVE_PROJECT, "dummy");
    String projectPath = "~/basic/build.gradle";
    GradleExecutionSettings settings = createMock(GradleExecutionSettings.class);
    ProjectConnection connection = createMock(ProjectConnection.class);

    Function<ProjectConnection,DataNode<ProjectData>> function = myFunctionFactory.createFunction(id, projectPath, settings);
    assertNotNull(function);

    DataNode<ProjectData> projectInfo = createMock(DataNode.class);

    // Verify that function execution delegates to ProjectResolverDelegates.
    expect(myStrategy1.resolveProjectInfo(id, projectPath, settings, connection)).andReturn(null);
    expect(myStrategy2.resolveProjectInfo(id, projectPath, settings, connection)).andReturn(projectInfo);
    replay(myStrategy1, myStrategy2);

    DataNode<ProjectData> resolved = function.fun(connection);

    verify(myStrategy1, myStrategy2);

    assertSame(projectInfo, resolved);
  }
}

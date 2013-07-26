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

import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter.NULL_OBJECT;
import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link ProjectResolverFunctionFactory}.
 */
public class ProjectResolverFunctionFactoryTest extends TestCase {
  private ProjectImportErrorHandler myErrorHandler;
  private ProjectResolver myStrategy;
  private ProjectResolverFunctionFactory myFunctionFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myErrorHandler = createMock(ProjectImportErrorHandler.class);
    myStrategy = createMock(ProjectResolver.class);
    myFunctionFactory = new ProjectResolverFunctionFactory(myStrategy);
  }

  @SuppressWarnings("unchecked")
  public void testCreateFunction() {
    ExternalSystemTaskId id = ExternalSystemTaskId.create(ExternalSystemTaskType.RESOLVE_PROJECT, "dummy");
    String projectPath = "~/basic/build.gradle";
    GradleExecutionSettings settings = createMock(GradleExecutionSettings.class);
    ProjectConnection connection = createMock(ProjectConnection.class);

    Function<ProjectConnection, DataNode<ProjectData>> function =
      myFunctionFactory.createFunction(id, projectPath, myErrorHandler, NULL_OBJECT, settings);
    assertNotNull(function);

    DataNode<ProjectData> projectInfo = createMock(DataNode.class);

    // Verify that function execution delegates to ProjectResolverDelegates.
    expect(myStrategy.resolveProjectInfo(id, projectPath, settings, connection, NULL_OBJECT)).andReturn(projectInfo);
    replay(myStrategy);

    DataNode<ProjectData> resolved = function.fun(connection);

    verify(myStrategy);

    assertSame(projectInfo, resolved);
  }
}

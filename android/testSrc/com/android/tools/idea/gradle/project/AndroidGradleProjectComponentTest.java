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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.mockito.Mock;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidGradleProjectComponent}.
 */
public class AndroidGradleProjectComponentTest extends IdeaTestCase {
  @Mock LegacyAndroidProjects myLegacyAndroidProjects;

  private IdeComponents myIdeComponents;
  private SupportedModuleChecker mySupportedModuleChecker;
  private GradleProjectInfo myGradleProjectInfo;
  private AndroidGradleProjectComponent myProjectComponent;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myIdeComponents = new IdeComponents(project);
    mySupportedModuleChecker = myIdeComponents.mockService(SupportedModuleChecker.class);
    myGradleProjectInfo = myIdeComponents.mockProjectService(GradleProjectInfo.class);

    myProjectComponent = new AndroidGradleProjectComponent(project, myLegacyAndroidProjects);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testEmpty() {
    // placeholder for disabled test below.
  }

  // failing after 2017.3 merge
  public void /*test*/ProjectOpenedWithProjectCreationError() {
    String projectCreationError = "Something went terribly wrong!";
    when(myGradleProjectInfo.getProjectCreationError()).thenReturn(projectCreationError);

    Project project = getProject();
    ExternalSystemNotificationManagerStub notificationManager = new ExternalSystemNotificationManagerStub(project);
    IdeComponents.replaceService(project, ExternalSystemNotificationManager.class, notificationManager);

    myProjectComponent.projectOpened();

    // http://b/62543339 http://b/62761000
    assertThat(notificationManager.error).isInstanceOf(ExternalSystemException.class);
    assertEquals(projectCreationError, notificationManager.error.getMessage());
    assertEquals(project.getName(), notificationManager.externalProjectName);
    assertSame(GradleConstants.SYSTEM_ID, notificationManager.externalSystemId);

    verify(myGradleProjectInfo, times(1)).setProjectCreationError(null);
    verify(mySupportedModuleChecker, times(1)).checkForSupportedModules(project);
    verify(myLegacyAndroidProjects, never()).trackProject();
  }

  private static class ExternalSystemNotificationManagerStub extends ExternalSystemNotificationManager {
    private Throwable error;
    private String externalProjectName;
    private ProjectSystemId externalSystemId;

    ExternalSystemNotificationManagerStub(@NotNull Project project) {
      super(project);
    }

    @Override
    public void processExternalProjectRefreshError(@NotNull Throwable error,
                                                   @NotNull String externalProjectName,
                                                   @NotNull ProjectSystemId externalSystemId) {
      this.error = error;
      this.externalProjectName = externalProjectName;
      this.externalSystemId = externalSystemId;
    }
  }
}
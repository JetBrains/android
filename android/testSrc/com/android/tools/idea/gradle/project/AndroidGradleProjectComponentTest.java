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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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
  @Mock private GradleProjectInfo myGradleProjectInfo;
  @Mock private AndroidProjectInfo myAndroidProjectInfo;
  @Mock private GradleSyncInvoker myGradleSyncInvoker;
  @Mock private GradleBuildInvoker myGradleBuildInvoker;
  @Mock private CompilerManager myCompilerManager;
  @Mock private SupportedModuleChecker mySupportedModuleChecker;
  @Mock private IdeInfo myIdeInfo;

  private ExternalSystemNotificationManagerStub myNotificationManager;
  private AndroidGradleProjectComponent myProjectComponent;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myNotificationManager = new ExternalSystemNotificationManagerStub(project);
    myProjectComponent =
      new AndroidGradleProjectComponent(project, myGradleProjectInfo, myAndroidProjectInfo, myNotificationManager, myGradleSyncInvoker,
                                        myGradleBuildInvoker, myCompilerManager, mySupportedModuleChecker, myIdeInfo,
                                        myLegacyAndroidProjects);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myNotificationManager);
      myProjectComponent.projectClosed();
    }
    finally {
      super.tearDown();
    }
  }

  public void testProjectOpenedWithProjectCreationError() {
    String projectCreationError = "Something went terribly wrong!";
    when(myGradleProjectInfo.getProjectCreationError()).thenReturn(projectCreationError);
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    Project project = getProject();
    IdeComponents.replaceService(project, ExternalSystemNotificationManager.class, myNotificationManager);

    myProjectComponent.projectOpened();

    // http://b/62543339 http://b/62761000
    assertThat(myNotificationManager.error).isInstanceOf(ExternalSystemException.class);
    assertEquals(projectCreationError, myNotificationManager.error.getMessage());
    assertEquals(project.getName(), myNotificationManager.externalProjectName);
    assertSame(GradleConstants.SYSTEM_ID, myNotificationManager.externalSystemId);

    verify(myGradleProjectInfo, times(1)).setProjectCreationError(null);
    verify(mySupportedModuleChecker, times(1)).checkForSupportedModules(project);
    verify(myLegacyAndroidProjects, never()).trackProject();
  }

  private static class ExternalSystemNotificationManagerStub extends ExternalSystemNotificationManager {
    @NotNull private final Project myProject;
    private Throwable error;
    private String externalProjectName;
    private ProjectSystemId externalSystemId;

    ExternalSystemNotificationManagerStub(@NotNull Project project) {
      super(project);
      myProject = project;
    }

    @Override
    public void processExternalProjectRefreshError(@NotNull Throwable error,
                                                   @NotNull String externalProjectName,
                                                   @NotNull ProjectSystemId externalSystemId) {
      if (myProject == null || myProject.isDisposed() || !myProject.isOpen()) {
        return;
      }
      this.error = error;
      this.externalProjectName = externalProjectName;
      this.externalSystemId = externalSystemId;
    }
  }
}
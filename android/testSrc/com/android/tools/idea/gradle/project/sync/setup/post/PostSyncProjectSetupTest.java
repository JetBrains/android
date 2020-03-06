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
package com.android.tools.idea.gradle.project.sync.setup.post;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_CACHED_SETUP_FAILED;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Tests for {@link PostSyncProjectSetup}.
 */
public class PostSyncProjectSetupTest extends PlatformTestCase {
  @Mock private IdeInfo myIdeInfo;
  @Mock private GradleProjectInfo myGradleProjectInfo;
  @Mock private GradleSyncInvoker mySyncInvoker;
  @Mock private GradleSyncState mySyncState;
  @Mock private ProjectSetup myProjectSetup;
  @Mock private GradleProjectBuilder myProjectBuilder;
  @Mock private RunManagerEx myRunManager;
  @Mock private ExternalSystemTaskId myTaskId;
  @Mock private SyncViewManager myViewManager;

  private PostSyncProjectSetup mySetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myRunManager = RunManagerImpl.getInstanceImpl(project);

    new IdeComponents(myProject).replaceProjectService(SyncViewManager.class, myViewManager);

    ProjectStructureStub projectStructure = new ProjectStructureStub(project);
    mySetup = new PostSyncProjectSetup(project, myIdeInfo, projectStructure, myGradleProjectInfo, mySyncInvoker, mySyncState,
                                       myProjectSetup, myRunManager);
  }

  @Override
  protected void tearDown() throws Exception {
    myRunManager = null;
    mySetup = null;
    super.tearDown();
  }

  public void testJUnitRunConfigurationSetup() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    mySetup.setUpProject(request, myTaskId, null);
    ConfigurationFactory configurationFactory = AndroidJUnitConfigurationType.getInstance().getConfigurationFactories()[0];
    Project project = getProject();
    AndroidJUnitConfiguration jUnitConfiguration = new AndroidJUnitConfiguration(project, configurationFactory);
    myRunManager.addConfiguration(myRunManager.createConfiguration(jUnitConfiguration, configurationFactory), true);

    List<RunConfiguration> junitRunConfigurations = myRunManager.getConfigurationsList(AndroidJUnitConfigurationType.getInstance());
    for (RunConfiguration runConfiguration : junitRunConfigurations) {
      assertSize(1, myRunManager.getBeforeRunTasks(runConfiguration));
      assertEquals(MakeBeforeRunTaskProvider.ID, myRunManager.getBeforeRunTasks(runConfiguration).get(0).getProviderId());
    }

    RunConfiguration runConfiguration = junitRunConfigurations.get(0);
    List<BeforeRunTask> tasks = new LinkedList<>(myRunManager.getBeforeRunTasks(runConfiguration));

    MakeBeforeRunTaskProvider taskProvider = new MakeBeforeRunTaskProvider(project);
    BeforeRunTask newTask = taskProvider.createTask(runConfiguration);
    newTask.setEnabled(true);
    tasks.add(newTask);
    myRunManager.setBeforeRunTasks(runConfiguration, tasks);

    mySetup.setUpProject(request, myTaskId, null);
    assertSize(2, myRunManager.getBeforeRunTasks(runConfiguration));

    verify(myGradleProjectInfo, times(2)).setNewProject(false);
    verify(myGradleProjectInfo, times(2)).setImportedProject(false);
  }

  // See: https://code.google.com/p/android/issues/detail?id=225938
  public void testSyncWithCachedModelsFinishedWithSyncIssues() {
    when(mySyncState.lastSyncFailed()).thenReturn(true);

    long lastSyncTimestamp = 2L;
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    request.usingCachedGradleModels = true;
    request.lastSyncTimestamp = lastSyncTimestamp;

    mySetup.setUpProject(request, myTaskId, null);

    verify(mySyncState, times(1)).syncSkipped(lastSyncTimestamp, null);
    verify(mySyncInvoker, times(1)).requestProjectSync(getProject(), TRIGGER_PROJECT_CACHED_SETUP_FAILED);
    verify(myProjectSetup, never()).setUpProject(true);

    verify(myGradleProjectInfo, times(1)).setNewProject(false);
    verify(myGradleProjectInfo, times(1)).setImportedProject(false);
  }

  public void testWithSyncIssueDuringProjectSetup() {
    // Simulate the case when sync issue happens during ProjectSetup.
    when(mySyncState.lastSyncFailed()).thenReturn(false).thenReturn(true);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    request.usingCachedGradleModels = false;
    request.lastSyncTimestamp = 1L;

    mySetup.setUpProject(request, myTaskId, null);

    verify(mySyncState, times(1)).syncFailed(any(), any(), any());
    verify(mySyncState, never()).syncSucceeded();
  }

  public void testWithExceptionDuringProjectSetup() {
    when(mySyncState.lastSyncFailed()).thenReturn(false);
    doThrow(new RuntimeException()).when(myProjectSetup).setUpProject(false);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    request.usingCachedGradleModels = false;
    request.lastSyncTimestamp = 1L;

    mySetup.setUpProject(request, myTaskId, null);

    verify(mySyncState, times(1)).syncFailed(any(), any(), any());
    verify(mySyncState, never()).syncSucceeded();
  }

  // See: https://code.google.com/p/android/issues/detail?id=225938
  public void testSyncFinishedWithSyncIssues() {
    when(mySyncState.lastSyncFailed()).thenReturn(true);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    mySetup.setUpProject(request, myTaskId, null);

    verify(myProjectSetup, times(1)).setUpProject(true);
    verify(mySyncState, times(1)).syncFailed(any(), any(), any());
    verify(mySyncState, never()).syncSucceeded();

    // Source generation should not be invoked if sync failed.
    verify(myProjectBuilder, never()).cleanAndGenerateSources();
    verify(myGradleProjectInfo, times(1)).setNewProject(false);
    verify(myGradleProjectInfo, times(1)).setImportedProject(false);
  }

  public void testEnsureFailedCachedSyncEmitsBuildFinishedEvent() {
    when(mySyncState.lastSyncFailed()).thenReturn(true);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    request.usingCachedGradleModels = true;
    mySetup.setUpProject(request, myTaskId, null);

    // Ensure the SyncViewManager was told about the sync finishing.
    ArgumentCaptor<BuildEvent> buildEventArgumentCaptor = ArgumentCaptor.forClass(BuildEvent.class);
    verify(myViewManager).onEvent(eq(myTaskId), buildEventArgumentCaptor.capture());

    assertInstanceOf(buildEventArgumentCaptor.getValue(), FinishBuildEvent.class);
    assertFalse(buildEventArgumentCaptor.getValue().getMessage().isEmpty());

    // Ensure a full sync is scheduled
    verify(mySyncInvoker).requestProjectSync(myProject, TRIGGER_PROJECT_CACHED_SETUP_FAILED);
  }

  private static class ProjectStructureStub extends ProjectStructure {
    AndroidPluginVersionsInProject agpVersionsFromPreviousSync = new AndroidPluginVersionsInProject();
    AndroidPluginVersionsInProject currentAgpVersions = new AndroidPluginVersionsInProject();

    boolean analyzed;

    ProjectStructureStub(@NotNull Project project) {
      super(project);
    }

    @Override
    public void analyzeProjectStructure() {
      analyzed = true;
    }

    @Override
    @NotNull
    public AndroidPluginVersionsInProject getAndroidPluginVersions() {
      return analyzed ? currentAgpVersions : agpVersionsFromPreviousSync;
    }
  }
}

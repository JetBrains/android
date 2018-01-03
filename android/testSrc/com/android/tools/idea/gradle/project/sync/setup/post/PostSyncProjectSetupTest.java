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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.ProjectStructure.AndroidPluginVersionsInProject;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker;
import com.android.tools.idea.gradle.project.sync.setup.module.common.DependencySetupIssues;
import com.android.tools.idea.gradle.project.sync.validation.common.CommonModuleValidator;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.instantapp.ProvisionTasks;
import com.android.tools.idea.instantapp.provision.ProvisionBeforeRunTaskProvider;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.google.common.collect.Lists;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.LinkedList;
import java.util.List;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_LOADED;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link PostSyncProjectSetup}.
 */
public class PostSyncProjectSetupTest extends IdeaTestCase {
  @Mock private IdeInfo myIdeInfo;
  @Mock private GradleProjectInfo myGradleProjectInfo;
  @Mock private GradleSyncInvoker mySyncInvoker;
  @Mock private GradleSyncState mySyncState;
  @Mock private DependencySetupIssues myDependencySetupIssues;
  @Mock private ProjectSetup myProjectSetup;
  @Mock private ModuleSetup myModuleSetup;
  @Mock private GradleSyncSummary mySyncSummary;
  @Mock private PluginVersionUpgrade myVersionUpgrade;
  @Mock private VersionCompatibilityChecker myVersionCompatibilityChecker;
  @Mock private GradleProjectBuilder myProjectBuilder;
  @Mock private CommonModuleValidator.Factory myModuleValidatorFactory;
  @Mock private CommonModuleValidator myModuleValidator;
  @Mock private RunManagerImpl myRunManager;
  @Mock private ProvisionTasks myProvisionTasks;

  private ProjectStructureStub myProjectStructure;
  private ProgressIndicator myProgressIndicator;
  private PostSyncProjectSetup mySetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myProgressIndicator = new MockProgressIndicator();

    Project project = getProject();
    myRunManager = RunManagerImpl.getInstanceImpl(project);
    myProvisionTasks = new ProvisionTasks();
    when(mySyncState.getSummary()).thenReturn(mySyncSummary);
    when(myModuleValidatorFactory.create(project)).thenReturn(myModuleValidator);

    myProjectStructure = new ProjectStructureStub(project);
    mySetup = new PostSyncProjectSetup(project, myIdeInfo, myProjectStructure, myGradleProjectInfo, mySyncInvoker, mySyncState,
                                       myDependencySetupIssues, myProjectSetup, myModuleSetup, myVersionUpgrade,
                                       myVersionCompatibilityChecker, myProjectBuilder, myModuleValidatorFactory, myRunManager,
                                       myProvisionTasks);
  }

  public void testJUnitRunConfigurationSetup() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    mySetup.setUpProject(request, myProgressIndicator);
    ConfigurationFactory configurationFactory = AndroidJUnitConfigurationType.getInstance().getConfigurationFactories()[0];
    Project project = getProject();
    AndroidJUnitConfiguration jUnitConfiguration = new AndroidJUnitConfiguration("", project, configurationFactory);
    myRunManager.addConfiguration(myRunManager.createConfiguration(jUnitConfiguration, configurationFactory), true);

    List<RunConfiguration> junitRunConfigurations = myRunManager.getConfigurationsList(AndroidJUnitConfigurationType.getInstance());
    for (RunConfiguration runConfiguration : junitRunConfigurations) {
      assertSize(1, myRunManager.getBeforeRunTasks(runConfiguration));
      assertEquals(MakeBeforeRunTaskProvider.ID, myRunManager.getBeforeRunTasks(runConfiguration).get(0).getProviderId());
    }

    RunConfiguration runConfiguration = junitRunConfigurations.get(0);
    List<BeforeRunTask> tasks = new LinkedList<>(myRunManager.getBeforeRunTasks(runConfiguration));

    MakeBeforeRunTaskProvider taskProvider = new MakeBeforeRunTaskProvider(project, AndroidProjectInfo.getInstance(project),
                                                                           GradleProjectInfo.getInstance(project));
    BeforeRunTask newTask = taskProvider.createTask(runConfiguration);
    newTask.setEnabled(true);
    tasks.add(newTask);
    myRunManager.setBeforeRunTasks(runConfiguration, tasks, false);

    mySetup.setUpProject(request, myProgressIndicator);
    assertSize(2, myRunManager.getBeforeRunTasks(runConfiguration));

    verify(myGradleProjectInfo, times(2)).setNewProject(false);
    verify(myGradleProjectInfo, times(2)).setImportedProject(false);
  }

  // See: https://code.google.com/p/android/issues/detail?id=225938
  public void testSyncWithCachedModelsFinishedWithSyncIssues() {
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(true);

    long lastSyncTimestamp = 2L;
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    request.usingCachedGradleModels = true;
    request.lastSyncTimestamp = lastSyncTimestamp;

    mySetup.setUpProject(request, myProgressIndicator);

    verify(mySyncState, times(1)).syncSkipped(lastSyncTimestamp);
    verify(mySyncInvoker, times(1)).requestProjectSyncAndSourceGeneration(getProject(), TRIGGER_PROJECT_LOADED);
    verify(myProjectSetup, never()).setUpProject(myProgressIndicator, true);

    verify(myGradleProjectInfo, times(1)).setNewProject(false);
    verify(myGradleProjectInfo, times(1)).setImportedProject(false);
  }

  public void testWithSyncIssueDuringProjectSetup() {
    // Simulate the case when sync issue happens during ProjectSetup.
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(false).thenReturn(true);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    request.usingCachedGradleModels = false;
    request.lastSyncTimestamp = 1L;

    mySetup.setUpProject(request, myProgressIndicator);

    verify(mySyncState, times(1)).syncFailed(any());
    verify(mySyncState, never()).syncEnded();
  }

  // See: https://code.google.com/p/android/issues/detail?id=225938
  public void testSyncFinishedWithSyncIssues() {
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(true);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    request.generateSourcesAfterSync = true;
    request.cleanProjectAfterSync = true;

    mySetup.setUpProject(request, myProgressIndicator);

    Project project = getProject();
    verify(myDependencySetupIssues, times(1)).reportIssues();
    verify(myVersionCompatibilityChecker, times(1)).checkAndReportComponentIncompatibilities(project);

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      verify(myModuleValidator, times(1)).validate(module);
    }

    verify(myModuleValidator, times(1)).fixAndReportFoundIssues();
    verify(myProjectSetup, times(1)).setUpProject(myProgressIndicator, true);
    verify(mySyncState, times(1)).syncFailed(any());
    verify(mySyncState, never()).syncEnded();

    // Source generation should not be invoked if sync failed.
    verify(myProjectBuilder, never()).cleanAndGenerateSources();

    verify(myGradleProjectInfo, times(1)).setNewProject(false);
    verify(myGradleProjectInfo, times(1)).setImportedProject(false);
  }

  public void testCleanIsInvokedWhenGeneratingSourcesAndPluginVersionsChanged() {
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(false);

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    request.generateSourcesAfterSync = true;

    myProjectStructure.currentAgpVersions = new AndroidPluginVersionsInProject() {
      @Override
      public boolean haveVersionsChanged(@NotNull AndroidPluginVersionsInProject other) {
        return true; // Simulate AGP versions have changed between Sync executions.
      }
    };

    mySetup.setUpProject(request, myProgressIndicator);

    // verify "clean" was invoked.
    verify(myProjectBuilder).cleanAndGenerateSources();

    assertTrue(myProjectStructure.analyzed);
  }

  public void testProvisionBeforeRunTaskIsAdded() {
    // Create android run configurations
    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    AndroidRunConfiguration androidRunConfiguration = new AndroidRunConfiguration(getProject(), configurationFactory);
    androidRunConfiguration.setName("androidRunConfiguration");
    AndroidRunConfiguration androidRunConfiguration2 = new AndroidRunConfiguration(getProject(), configurationFactory);
    androidRunConfiguration.setName("androidRunConfiguration2");
    myRunManager.addConfiguration(myRunManager.createConfiguration(androidRunConfiguration, configurationFactory), true);
    myRunManager.addConfiguration(myRunManager.createConfiguration(androidRunConfiguration2, configurationFactory), true);

    // Provision before run task is created automatically when run configurations are created
    assertSize(1, myRunManager.getBeforeRunTasks(androidRunConfiguration, ProvisionBeforeRunTaskProvider.ID));
    assertSize(1, myRunManager.getBeforeRunTasks(androidRunConfiguration2, ProvisionBeforeRunTaskProvider.ID));

    // Reset only in one of the configurations (e.g. open old projects with run configs already created)
    myRunManager.setBeforeRunTasks(androidRunConfiguration, Lists.newArrayList());
    myRunManager.fireBeforeRunTasksUpdated();

    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    mySetup.setUpProject(request, myProgressIndicator);

    assertSize(1, myRunManager.getBeforeRunTasks(androidRunConfiguration, ProvisionBeforeRunTaskProvider.ID));
    assertSize(1, myRunManager.getBeforeRunTasks(androidRunConfiguration2, ProvisionBeforeRunTaskProvider.ID));
  }

  private static class ProjectStructureStub extends ProjectStructure {
    AndroidPluginVersionsInProject agpVersionsFromPreviousSync = new AndroidPluginVersionsInProject();
    AndroidPluginVersionsInProject currentAgpVersions = new AndroidPluginVersionsInProject();

    boolean analyzed;

    ProjectStructureStub(@NotNull Project project) {
      super(project);
    }

    @Override
    public void analyzeProjectStructure(@NotNull ProgressIndicator progressIndicator) {
      analyzed = true;
    }

    @Override
    @NotNull
    public AndroidPluginVersionsInProject getAndroidPluginVersions() {
      return analyzed ? currentAgpVersions : agpVersionsFromPreviousSync;
    }
  }
}
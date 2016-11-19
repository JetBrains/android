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

import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.common.DependencySetupErrors;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.PluginVersionUpgrade;
import com.android.tools.idea.gradle.project.sync.validation.common.CommonModuleValidator;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.LinkedList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.SDK_SETUP_ISSUES;
import static com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup.Request.DEFAULT_REQUEST;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link PostSyncProjectSetup}.
 */
public class PostSyncProjectSetupTest extends IdeaTestCase {
  @Mock private AndroidSdks myAndroidSdks;
  @Mock private GradleSyncInvoker mySyncInvoker;
  @Mock private GradleSyncState mySyncState;
  @Mock private DependencySetupErrors myDependencySetupErrors;
  @Mock private GradleSyncSummary mySyncSummary;
  @Mock private PluginVersionUpgrade myVersionUpgrade;
  @Mock private SyncMessages mySyncMessages;
  @Mock private DependenciesModuleSetupStep myModuleSetupStep;
  @Mock private VersionCompatibilityChecker myVersionCompatibilityChecker;
  @Mock private GradleProjectBuilder myProjectBuilder;
  @Mock private CommonModuleValidator.Factory myModuleValidatorFactory;
  @Mock private CommonModuleValidator myModuleValidator;
  @Mock private RunManagerImpl myRunManager;

  private PostSyncProjectSetup mySetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myRunManager = RunManagerImpl.getInstanceImpl(project);
    when(mySyncState.getSummary()).thenReturn(mySyncSummary);
    when(myModuleValidatorFactory.create(project)).thenReturn(myModuleValidator);

    PluginVersionUpgrade[] versionUpgrades = {myVersionUpgrade};

    mySetup =
      new PostSyncProjectSetup(project, myAndroidSdks, mySyncInvoker, mySyncState, myDependencySetupErrors, versionUpgrades, mySyncMessages,
                               myModuleSetupStep, myVersionCompatibilityChecker, myProjectBuilder, myModuleValidatorFactory, myRunManager);
  }

  public void testJUnitRunConfigurationSetup() {
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    mySetup.setUpProject(request);
    ConfigurationFactory configurationFactory = JUnitConfigurationType.getInstance().getConfigurationFactories()[0];
    JUnitConfiguration jUnitConfiguration = new JUnitConfiguration("", getProject(), configurationFactory);
    myRunManager.addConfiguration(myRunManager.createConfiguration(jUnitConfiguration, configurationFactory), true);

    RunConfiguration[] junitRunConfigurations = myRunManager.getConfigurations(JUnitConfigurationType.getInstance());
    for (RunConfiguration runConfiguration : junitRunConfigurations) {
      assertSize(1, myRunManager.getBeforeRunTasks(runConfiguration));
      assertEquals(MakeBeforeRunTaskProvider.ID, myRunManager.getBeforeRunTasks(runConfiguration).get(0).getProviderId());
    }

    RunConfiguration runConfiguration = junitRunConfigurations[0];
    List<BeforeRunTask> tasks = new LinkedList<>(myRunManager.getBeforeRunTasks(runConfiguration));
    BeforeRunTask newTask = new MakeBeforeRunTaskProvider(getProject()).createTask(runConfiguration);
    newTask.setEnabled(true);
    tasks.add(newTask);
    myRunManager.setBeforeRunTasks(runConfiguration, tasks, false);

    mySetup.setUpProject(request);
    assertSize(2, myRunManager.getBeforeRunTasks(runConfiguration));
  }

  // See: https://code.google.com/p/android/issues/detail?id=225938
  public void testSyncWithCachedModelsFinishedWithSyncIssues() {
    simulateSyncFinishedWithIssues();

    long lastSyncTimestamp = 2L;
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    // @formatter:off
    request.setUsingCachedGradleModels(true)
           .setLastSyncTimestamp(lastSyncTimestamp);
    // @formatter:on

    mySetup.setUpProject(request);

    verify(mySyncState, times(1)).syncSkipped(lastSyncTimestamp);
    verify(mySyncInvoker, times(1)).requestProjectSyncAndSourceGeneration(getProject(), null);
  }

  // See: https://code.google.com/p/android/issues/detail?id=225938
  public void testSyncFinishedWithSyncIssues() {
    simulateSyncFinishedWithIssues();

    // Avoid adding a hyperlink to install any missing platforms.
    when(mySyncMessages.getMessageCount(SDK_SETUP_ISSUES)).thenReturn(0);

    // Avoid check SDK tools version
    PostSyncProjectSetup.ourNewSdkVersionToolsInfoAlreadyShown = true;

    mySetup.setUpProject(DEFAULT_REQUEST);
  }

  private void simulateSyncFinishedWithIssues() {
    when(mySyncState.lastSyncFailed()).thenReturn(false);
    when(mySyncSummary.hasSyncErrors()).thenReturn(true);
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();

    // @formatter:off
    request.setGenerateSourcesAfterSync(true)
           .setCleanProjectAfterSync(true);
    // @formatter:on

    mySetup.setUpProject(request);

    verify(mySyncState, times(1)).syncEnded();

    // Source generation should not be invoked if sync failed.
    verify(myProjectBuilder, never()).generateSourcesOnly(true);
  }
}
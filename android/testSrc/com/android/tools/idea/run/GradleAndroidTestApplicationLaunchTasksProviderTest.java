/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.debug.impl.java.AndroidJavaDebugger;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType;
import com.android.tools.idea.testartifacts.instrumented.GradleAndroidTestApplicationLaunchTasksProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class GradleAndroidTestApplicationLaunchTasksProviderTest extends AndroidGradleTestCase {
  private final IDevice mockDevice = mock(IDevice.class);
  private final ProgramRunner runner = new DefaultStudioProgramRunner();

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadProject();

    if (myAndroidFacet == null) {
      fail("AndroidFacet was null");
    }
  }

  private void loadProject() throws Exception {
    super.loadProject(DYNAMIC_APP);
    // Run build task for main variant.
    String taskName = GradleAndroidModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName);
  }

  @NotNull
  private static String getDebuggerType() {
    return AndroidJavaDebugger.ID;
  }

  public void testLaunchTaskProvidedForDebugAllInModuleTest() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("debugAllInModuleTest", AndroidTestRunConfigurationType.getInstance().getFactory());
    AndroidTestRunConfiguration config = (AndroidTestRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    config.getAndroidDebuggerContext().setDebuggerType(getDebuggerType());
    config.TESTING_TYPE = AndroidTestRunConfiguration.TEST_ALL_IN_MODULE;

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      env,
      myAndroidFacet,
      appIdProvider
    );

    assertNotNull(provider.getTask(mockDevice));
  }

  public void testLaunchTaskProvidedForAllInPackageTest() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("allInPackageTest", AndroidTestRunConfigurationType.getInstance().getFactory());
    AndroidTestRunConfiguration config = (AndroidTestRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    config.TESTING_TYPE = AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE;


    Executor ex = DefaultRunExecutor.getRunExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      env,
      myAndroidFacet,
      appIdProvider);

    assertNotNull(provider.getTask(mockDevice));
  }

  public void testLaunchTaskProvidedForClassTest() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("classTest", AndroidTestRunConfigurationType.getInstance().getFactory());
    AndroidTestRunConfiguration config = (AndroidTestRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    config.TESTING_TYPE = AndroidTestRunConfiguration.TEST_CLASS;


    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      env,
      myAndroidFacet,
      appIdProvider
    );

    assertNotNull(provider.getTask(mockDevice));
  }

  public void testLaunchTaskProvidedForMethodTest() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("methodTest", AndroidTestRunConfigurationType.getInstance().getFactory());
    AndroidTestRunConfiguration config = (AndroidTestRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    config.TESTING_TYPE = AndroidTestRunConfiguration.TEST_METHOD;

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = getApplicationIdProvider(config);

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setDebug(false)
      .build();

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      env,
      myAndroidFacet,
      appIdProvider);

    assertNotNull(provider.getTask(mockDevice));
  }

  public void testNoLaunchTaskProvidedForIndeterminatePackageName() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("methodTest", AndroidTestRunConfigurationType.getInstance().getFactory());
    AndroidTestRunConfiguration config = (AndroidTestRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());


    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = mock(ApplicationIdProvider.class);
    when(appIdProvider.getTestPackageName()).thenReturn(null);

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      env,
      myAndroidFacet,
      appIdProvider);

    try {
      provider.getTask(mockDevice);
      fail();
    }
    catch (ExecutionException e) {
      assertThat(e.getMessage()).isEqualTo("Unable to determine test package name");
    }
  }

  public void testNoLaunchTaskProvidedWhenApkProvisionExceptionThrown() throws Exception {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createConfiguration("methodTest", AndroidTestRunConfigurationType.getInstance().getFactory());
    AndroidTestRunConfiguration config = (AndroidTestRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());


    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());
    env.putCopyableUserData(DeviceFutures.KEY, DeviceFutures.forDevices(ImmutableList.of(mockDevice)));

    ApplicationIdProvider appIdProvider = mock(ApplicationIdProvider.class);
    when(appIdProvider.getTestPackageName()).thenThrow(new ApkProvisionException("unable to determine package name"));

    GradleAndroidTestApplicationLaunchTasksProvider provider = new GradleAndroidTestApplicationLaunchTasksProvider(
      env,
      myAndroidFacet,
      appIdProvider
    );

    try {
      provider.getTask(mockDevice);
      fail();
    }
    catch (ExecutionException e) {
      assertThat(e.getMessage()).isEqualTo("Unable to determine test package name");
    }
  }

  @NotNull
  private ApplicationIdProvider getApplicationIdProvider(@NotNull AndroidRunConfigurationBase runConfiguration) {
    return Objects.requireNonNull(getProjectSystem(myAndroidFacet.getModule().getProject()).getApplicationIdProvider(runConfiguration));
  }
}

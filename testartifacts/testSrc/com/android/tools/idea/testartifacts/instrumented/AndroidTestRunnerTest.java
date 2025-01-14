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
package com.android.tools.idea.testartifacts.instrumented;

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;
import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.AndroidTestOrchestratorRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.DefaultStudioProgramRunner;
import com.android.tools.idea.run.FakeAndroidDevice;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.SystemInfo;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for the Android Test Runner related things.
 * <p>
 * TODO: Delete this file and merge it into AndroidTestRunConfigurationTest.
 */
public class AndroidTestRunnerTest extends AndroidGradleTestCase {
  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  public void testTestOptionsSetByGradle() throws Exception {
    loadProject(TestProjectPaths.RUN_CONFIG_RUNNER_ARGUMENTS);

    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("com.android.runnerarguments.ExampleInstrumentationTest");
    assertThat(runner.getAmInstrumentCommand()).contains("--no-window-animation");
  }

  public void testRunnerIsObtainedFromGradleProjects() throws Exception {
    loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);

    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("google.testapplication.ApplicationTest");
    assertThat(runner.getRunnerName()).isEqualTo("android.support.test.runner.AndroidJUnitRunner");
  }

  public void testRunnerObtainedFromGradleCanBeOverridden() throws Exception {
    loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);
    AndroidTestRunConfiguration config = createConfigFromClass("google.testapplication.ApplicationTest");
    config.INSTRUMENTATION_RUNNER_CLASS = "my.awesome.CustomTestRunner";

    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner(config);
    assertThat(runner.getRunnerName()).isEqualTo("my.awesome.CustomTestRunner");
  }

  public void testRunnerAtoNotUsed() throws Exception {
    loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("google.testapplication.ApplicationTest");
    assertThat(runner).isInstanceOf(RemoteAndroidTestRunner.class);
  }

  public void testRunnerAtoUsed() throws Exception {
    loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER_ANDROID_TEST_ORCHESTRATOR);
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("google.testapplication.ApplicationTest");
    assertThat(runner).isInstanceOf(AndroidTestOrchestratorRemoteAndroidTestRunner.class);
  }

  public void testRunnerCorrectForTestOnlyModule() throws Exception {
    loadProject(TestProjectPaths.TEST_ONLY_MODULE);
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("com.example.android.app.ExampleTest");
    assertThat(runner).isInstanceOf(RemoteAndroidTestRunner.class);
  }

  private RemoteAndroidTestRunner createRemoteAndroidTestRunner(@NotNull String className) throws ApkProvisionException {
    return createRemoteAndroidTestRunner(createConfigFromClass(className));
  }

  private RemoteAndroidTestRunner createRemoteAndroidTestRunner(AndroidTestRunConfiguration config) throws ApkProvisionException {
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(26));

    ApplicationIdProvider applicationIdProvider =
      getProjectSystem(myAndroidFacet.getModule().getProject()).getApplicationIdProvider(config);

    final RunnerAndConfigurationSettings configuration =
      RunManager.getInstance(getProject()).createConfiguration(config, AndroidTestRunConfigurationType.getInstance().getFactory());
    ExecutionEnvironment env =
      new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), new DefaultStudioProgramRunner(), configuration, getProject());
    AndroidTestApplicationLaunchTask androidTestTask = null;
    try {
      androidTestTask = ((AndroidTestRunConfigurationExecutor)config.getExecutor(env, myAndroidFacet,
                                                                                 FakeAndroidDevice.forDevices(
                                                                                   Arrays.asList(mockDevice)))).getApplicationLaunchTask(
        applicationIdProvider.getTestPackageName());
    }
    catch (ExecutionException e) {
      fail(e.getMessage());
    }
    assertThat(androidTestTask).isInstanceOf(AndroidTestApplicationLaunchTask.class);
    return androidTestTask.createRemoteAndroidTestRunner(mockDevice);
  }

  private AndroidTestRunConfiguration createConfigFromClass(String className) {
    AndroidTestRunConfiguration androidTestRunConfiguration = createAndroidTestConfigurationFromClass(getProject(), className);
    assertNotNull(androidTestRunConfiguration);
    return androidTestRunConfiguration;
  }
}

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

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.AndroidTestOrchestratorRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.android.tools.idea.run.GradleApplicationIdProvider;
import com.android.tools.idea.run.editor.AndroidRunConfigurationEditor;
import com.android.tools.idea.run.editor.TestRunParameters;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for the Android Test Runner related things
 */
public class AndroidTestRunnerTest extends AndroidGradleTestCase {
  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  public void testRunnerComponentsHiddenWhenGradleProject() throws Exception {
    loadSimpleApplication();

    AndroidTestRunConfiguration androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.ApplicationTest");
    assertNotNull(androidTestRunConfiguration);

    AndroidRunConfigurationEditor<?> editor =
      (AndroidRunConfigurationEditor<?>)androidTestRunConfiguration.getConfigurationEditor();

    TestRunParameters testRunParameters = (TestRunParameters)editor.getConfigurationSpecificEditor();
    testRunParameters.resetFrom(androidTestRunConfiguration);
    assertFalse("Runner component is visible in a Gradle project", testRunParameters.getRunnerComponent().isVisible());
  }

  public void testRunnerArgumentsSet() throws Exception {
    loadProject(TestProjectPaths.RUN_CONFIG_RUNNER_ARGUMENTS);

    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("com.android.runnerarguments.ExampleInstrumentationTest");
    assertThat(runner.getAmInstrumentCommand()).contains("-e size medium");
    assertThat(runner.getAmInstrumentCommand()).contains("-e foo bar");
  }

  public void testRunnerComponentsShouldBeObtainedFromGradleProjects() throws Exception {
    loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);

    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("google.testapplication.ApplicationTest");
    assertThat(runner.getRunnerName()).isEqualTo("android.support.test.runner.AndroidJUnitRunner");
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

  private RemoteAndroidTestRunner createRemoteAndroidTestRunner(@NotNull String className) {
    return createLaunchTask(className).createRemoteAndroidTestRunner(mock(IDevice.class));
  }

  private AndroidTestApplicationLaunchTask createLaunchTask(@NotNull String className) {
    ApplicationIdProvider applicationIdProvider = new GradleApplicationIdProvider(myAndroidFacet);
    LaunchStatus launchStatus = new ProcessHandlerLaunchStatus(new NopProcessHandler());

    AndroidTestRunConfiguration androidTestRunConfiguration = createAndroidTestConfigurationFromClass(getProject(), className);
    assertNotNull(androidTestRunConfiguration);

    return (AndroidTestApplicationLaunchTask)androidTestRunConfiguration
      .getApplicationLaunchTask(applicationIdProvider, myAndroidFacet, "",
                                false, launchStatus);
  }
}

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

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.OnDeviceOrchestratorRemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
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

import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static org.mockito.Mockito.mock;

/**
 * Tests for the Android Test Runner related things
 */
public class AndroidTestRunnerTest extends AndroidGradleTestCase {
  @Override
  protected boolean shouldRunTest() {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  // failing after 2017.3 merge
  public void /*test*/RunnerComponentsHiddenWhenGradleProject() throws Exception {
    loadSimpleApplication();

    AndroidTestRunConfiguration androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), "google.simpleapplication.ApplicationTest");
    assertNotNull(androidTestRunConfiguration);

    AndroidRunConfigurationEditor<AndroidTestRunConfiguration> editor =
      (AndroidRunConfigurationEditor<AndroidTestRunConfiguration>)androidTestRunConfiguration.getConfigurationEditor();

    TestRunParameters testRunParameters = (TestRunParameters)editor.getConfigurationSpecificEditor();
    testRunParameters.resetFrom(androidTestRunConfiguration);
    assertFalse("Runner component is visible in a Gradle project", testRunParameters.getRunnerComponent().isVisible());
  }

  public void testRunnerArgumentsSet() throws Exception {
    loadProject(TestProjectPaths.RUN_CONFIG_RUNNER_ARGUMENTS);
    Map<String, String> expectedArguments = new HashMap<>();
    expectedArguments.put("size", "medium");
    expectedArguments.put("foo", "bar");

    Map<String, String> runnerArguments = AndroidTestRunConfiguration.getRunnerArguments(myAndroidFacet);
    assertEquals(expectedArguments, runnerArguments);
  }

  // failing after 2017.3 merge
  public void /*test*/RunnerComponentsEmptyForGradleProjects() throws Exception {
    loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);

    AndroidTestRunConfiguration androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), "google.testapplication.ApplicationTest");
    assertNotNull(androidTestRunConfiguration);

    assertEmpty(androidTestRunConfiguration.INSTRUMENTATION_RUNNER_CLASS);
  }

  // failing after 2017.3 merge
  public void /*test*/RunnerOdoNotUsed() throws Exception {
    loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);
    AndroidTestRunConfiguration.MyApplicationLaunchTask task = createLaunchTask("google.testapplication.ApplicationTest");
    IDevice device = mock(IDevice.class);

    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(myAndroidFacet);
    assertNotNull(androidModuleModel);
    IdeAndroidArtifact artifact = androidModuleModel.getSelectedVariant().getMainArtifact();
    assertInstanceOf(task.getRemoteAndroidTestRunner(artifact, device), RemoteAndroidTestRunner.class);
  }

  // failing after 2017.3 merge
  public void /*test*/RunnerOdoUsed() throws Exception {
    loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER_ANDROID_TEST_ORCHESTRATOR);
    AndroidTestRunConfiguration.MyApplicationLaunchTask task = createLaunchTask("google.testapplication.ApplicationTest");
    IDevice device = mock(IDevice.class);

    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(myAndroidFacet);
    assertNotNull(androidModuleModel);
    IdeAndroidArtifact artifact = androidModuleModel.getSelectedVariant().getAndroidTestArtifact();
    assertInstanceOf(task.getRemoteAndroidTestRunner(artifact, device), OnDeviceOrchestratorRemoteAndroidTestRunner.class);
  }

  // failing after 2017.3 merge
  public void /*test*/RunnerCorrectForTestOnlyModule() throws Exception {
    loadProject(TestProjectPaths.TEST_ONLY_MODULE);
    AndroidTestRunConfiguration.MyApplicationLaunchTask task = createLaunchTask("com.example.android.app.ExampleTest");
    IDevice device = mock(IDevice.class);

    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(myAndroidFacet);
    assertNotNull(androidModuleModel);
    IdeAndroidArtifact artifact = androidModuleModel.getSelectedVariant().getMainArtifact();
    assertInstanceOf(task.getRemoteAndroidTestRunner(artifact, device), RemoteAndroidTestRunner.class);
  }

  private AndroidTestRunConfiguration.MyApplicationLaunchTask createLaunchTask(@NotNull String className) {
    ApplicationIdProvider applicationIdProvider = new GradleApplicationIdProvider(myAndroidFacet);
    LaunchStatus launchStatus = new ProcessHandlerLaunchStatus(new NopProcessHandler());

    AndroidTestRunConfiguration androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), className);
    assertNotNull(androidTestRunConfiguration);


    return (AndroidTestRunConfiguration.MyApplicationLaunchTask) androidTestRunConfiguration.getApplicationLaunchTask(applicationIdProvider, myAndroidFacet, false, launchStatus);
  }
}

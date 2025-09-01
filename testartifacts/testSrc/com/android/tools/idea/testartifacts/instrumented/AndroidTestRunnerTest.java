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
import static com.android.tools.idea.testing.AndroidGradleProjectRuleKt.onEdt;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
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
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.EdtAndroidGradleProjectRule;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the Android Test Runner related things.
 * <p>
 * TODO: Delete this file and merge it into AndroidTestRunConfigurationTest.
 */
@RunsInEdt
public class AndroidTestRunnerTest {
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  @Rule
  public EdtAndroidGradleProjectRule rule = onEdt(projectRule);

  @Before
  public void assumeNotWindows() {
    Assume.assumeFalse(SystemInfo.isWindows);
  }

  @Test
  public void testTestOptionsSetByGradle() throws Exception {
    projectRule.loadProject(TestProjectPaths.RUN_CONFIG_RUNNER_ARGUMENTS);
    AndroidFacet facet = projectRule.androidTestAndroidFacet(":app");
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("com.android.runnerarguments.ExampleInstrumentationTest", facet);
    assertThat(runner.getAmInstrumentCommand()).contains("--no-window-animation");
  }

  @Test
  public void testRunnerIsObtainedFromGradleProjects() throws Exception {
    projectRule.loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);
    AndroidFacet facet = projectRule.androidTestAndroidFacet(":app");
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("google.testapplication.ApplicationTest", facet);
    assertThat(runner.getRunnerName()).isEqualTo("android.support.test.runner.AndroidJUnitRunner");
  }

  @Test
  public void testRunnerObtainedFromGradleCanBeOverridden() throws Exception {
    projectRule.loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);
    AndroidTestRunConfiguration config = createConfigFromClass("google.testapplication.ApplicationTest");
    config.INSTRUMENTATION_RUNNER_CLASS = "my.awesome.CustomTestRunner";
    AndroidFacet facet = projectRule.androidTestAndroidFacet(":app");
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner(config, facet);
    assertThat(runner.getRunnerName()).isEqualTo("my.awesome.CustomTestRunner");
  }

  @Test
  public void testRunnerAtoNotUsed() throws Exception {
    projectRule.loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER);
    AndroidFacet facet = projectRule.androidTestAndroidFacet(":app");
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("google.testapplication.ApplicationTest", facet);
    assertThat(runner).isInstanceOf(RemoteAndroidTestRunner.class);
  }

  @Test
  public void testRunnerAtoUsed() throws Exception {
    Function1<File, Unit> androidTestOrchestratorPatch = (root) -> {
      File appBuildFile = new File(root, "app/build.gradle");
      String text;
      try {
        text = Files.readString(appBuildFile.toPath());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      StringBuilder sb = new StringBuilder(text);
      sb.append("\nandroid.testOptions.execution \"android_test_orchestrator\"");
      try {
        Files.writeString(appBuildFile.toPath(), sb.toString());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return Unit.INSTANCE;
    };
    projectRule.loadProject(TestProjectPaths.INSTRUMENTATION_RUNNER, null, null, androidTestOrchestratorPatch);
    AndroidFacet facet = projectRule.androidTestAndroidFacet(":app");
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("google.testapplication.ApplicationTest", facet);
    assertThat(runner).isInstanceOf(AndroidTestOrchestratorRemoteAndroidTestRunner.class);
  }

  @Test
  public void testRunnerCorrectForTestOnlyModule() throws Exception {
    projectRule.loadProject(TestProjectPaths.TEST_ONLY_MODULE);
    AndroidFacet facet = projectRule.mainAndroidFacet(":test");
    RemoteAndroidTestRunner runner = createRemoteAndroidTestRunner("com.example.android.app.ExampleTest", facet);
    assertThat(runner).isInstanceOf(RemoteAndroidTestRunner.class);
  }

  private RemoteAndroidTestRunner createRemoteAndroidTestRunner(
    @NotNull String className,
    @NotNull AndroidFacet facet
  ) throws ApkProvisionException {
    return createRemoteAndroidTestRunner(createConfigFromClass(className), facet);
  }

  private RemoteAndroidTestRunner createRemoteAndroidTestRunner(
    AndroidTestRunConfiguration config,
    @NotNull AndroidFacet facet
  ) throws ApkProvisionException {
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(26));

    Project project = projectRule.getProject();
    ApplicationIdProvider applicationIdProvider =
      getProjectSystem(project).getApplicationIdProvider(config);

    final RunnerAndConfigurationSettings configuration =
      RunManager.getInstance(project).createConfiguration(config, AndroidTestRunConfigurationType.getInstance().getFactory());
    ExecutionEnvironment env =
      new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), new DefaultStudioProgramRunner(), configuration, project);
    AndroidTestApplicationLaunchTask androidTestTask = null;
    try {
      androidTestTask =
        ((AndroidTestRunConfigurationExecutor) config.getExecutor(env, facet, FakeAndroidDevice.forDevices(Arrays.asList(mockDevice))))
        .getApplicationLaunchTask(applicationIdProvider.getTestPackageName());
    }
    catch (ExecutionException e) {
      fail(e.getMessage());
    }
    assertThat(androidTestTask).isInstanceOf(AndroidTestApplicationLaunchTask.class);
    return androidTestTask.createRemoteAndroidTestRunner(mockDevice);
  }

  private AndroidTestRunConfiguration createConfigFromClass(String className) {
    AndroidTestRunConfiguration androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(projectRule.getProject(), className);
    assertThat(androidTestRunConfiguration).isNotNull();
    return androidTestRunConfiguration;
  }
}

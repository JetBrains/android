/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.idea.run.tasks.*;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

import java.util.List;

import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.google.common.truth.Truth.assertThat;

public class AndroidLaunchTaskProviderTest extends AndroidGradleTestCase {

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
  }

  @NotNull
  private static String getDebuggerType() {
    return AndroidJavaDebugger.ID;
  }

  public void testDynamicAppApks() throws Exception {
    // Prepare
    final boolean debug = true;

    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(getProject()).
      createRunConfiguration("debug", AndroidRunConfigurationType.getInstance().getFactory());
    AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();
    config.setModule(myAndroidFacet.getModule());
    configSettings.checkSettings();
    if (debug) {
      config.getAndroidDebuggerContext().setDebuggerType(getDebuggerType());
    }

    ProgramRunner runner = new AndroidProgramRunner();

    Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
    ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, getProject());

    ApplicationIdProvider appIdProvider = new GradleApplicationIdProvider(myAndroidFacet);

    ApkProvider apkProvider = new GradleApkProvider(myAndroidFacet, appIdProvider, false);

    LaunchOptions launchOptions = LaunchOptions.builder()
      .setClearLogcatBeforeStart(false)
      .setSkipNoopApkInstallations(true)
      .setForceStopRunningApp(true)
      .setDebug(debug)
      .build();

    AndroidLaunchTasksProvider provider = new AndroidLaunchTasksProvider(
      (AndroidRunConfiguration)configSettings.getConfiguration(),
      env,
      myAndroidFacet,
      null,
      appIdProvider,
      apkProvider,
      launchOptions);

    IDevice device = Mockito.mock(IDevice.class);
    LaunchStatus launchStatus = new MyLaunchStatus();
    ConsolePrinter consolePrinter = new MyConsolePrinter();

    // Act
    List<LaunchTask> launchTasks = provider.getTasks(device, launchStatus, consolePrinter);

    // Assert
    assertThat(launchTasks.size()).isEqualTo(3);
    assertThat(launchTasks.get(0)).isInstanceOf(DismissKeyguardTask.class);
    assertThat(launchTasks.get(1)).isInstanceOf(SplitApkDeployTask.class);
    assertThat(launchTasks.get(2)).isInstanceOf(DefaultActivityLaunchTask.class);

    SplitApkDeployTask deployTask = (SplitApkDeployTask)launchTasks.get(1);
    assertThat(deployTask.getContext()).isInstanceOf(DynamicAppDeployTaskContext.class);

    DynamicAppDeployTaskContext context = (DynamicAppDeployTaskContext)deployTask.getContext();
    assertThat(context.getApplicationId()).isEqualTo("com.example.app");
    assertThat(context.getArtifacts().size()).isEqualTo(2);
    assertThat(context.getArtifacts().get(0).getName()).isEqualTo("app-debug.apk");
    assertThat(context.getArtifacts().get(1).getName()).isEqualTo("feature1-debug.apk");
  }

  private static class MyConsolePrinter implements ConsolePrinter {
    @Override
    public void stdout(@NotNull String message) {

    }

    @Override
    public void stderr(@NotNull String message) {

    }
  }

  private static class MyLaunchStatus implements LaunchStatus {
    @Override
    public boolean isLaunchTerminated() {
      return false;
    }

    @Override
    public void terminateLaunch(@Nullable String reason) {

    }
  }
}

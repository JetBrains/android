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

import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import org.jetbrains.annotations.NotNull;

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
    // Run build task for main variant.
    String taskName = AndroidModuleModel.get(myAndroidFacet).getSelectedVariant().getMainArtifact().getAssembleTaskName();
    invokeGradleTasks(getProject(), taskName);
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

    ProgramRunner runner = new DefaultStudioProgramRunner();

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
      appIdProvider,
      apkProvider,
      launchOptions);

    IDevice device = mock(IDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(26, null));
    LaunchStatus launchStatus = mock(LaunchStatus.class);
    ConsolePrinter consolePrinter = mock(ConsolePrinter.class);

    // Act
    List<LaunchTask> launchTasks = provider.getTasks(device, launchStatus, consolePrinter);

    // Assert
    launchTasks.forEach(task -> Logger.getInstance(this.getClass()).info("LaunchTask: " + task));
  }
}

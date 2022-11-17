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

import static com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.openPreparedTestProject;
import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.MakeBeforeRunTaskProviderTestUtilKt.mockDeviceFor;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.android.ddmlib.IDevice;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.android.tools.idea.testing.MakeBeforeRunTaskProviderTestUtilKt;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import kotlin.Unit;
import one.util.streamex.MoreCollectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

public class AndroidLaunchTaskProviderTest {
  @Rule
  public IntegrationTestEnvironmentRule projectRule = AndroidProjectRule.withIntegrationTestEnvironment();

  @NotNull
  private static String getDebuggerType() {
    return AndroidJavaDebugger.ID;
  }

  @Test
  public void testDynamicAppApks() throws Exception {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.DYNAMIC_APP);
    openPreparedTestProject(preparedProject, project -> {
      AndroidFacet androidFacet = AndroidFacet.getInstance(gradleModule(project, ":app"));
      // Prepare
      final boolean debug = true;

      RunnerAndConfigurationSettings configSettings =
        RunManager.getInstance(project)
          .getAllSettings()
          .stream()
          .filter(it -> it.getConfiguration() instanceof AndroidRunConfiguration)
          .collect(MoreCollectors.onlyOne())
          .get();
      AndroidRunConfiguration config = (AndroidRunConfiguration)configSettings.getConfiguration();

      config.setModule(androidFacet.getModule());
      try {
        configSettings.checkSettings();
      }
      catch (RuntimeConfigurationException e) {
        fail(e.getMessage());
      }
      if (debug) {
        config.getAndroidDebuggerContext().setDebuggerType(getDebuggerType());
      }
      IDevice device = mockDeviceFor(26, ImmutableList.of(Abi.X86, Abi.X86_64));
      MakeBeforeRunTaskProviderTestUtilKt.executeMakeBeforeRunStepInTest(config, device);

      ProgramRunner<RunnerSettings> runner = new DefaultStudioProgramRunner();

      Executor ex = DefaultDebugExecutor.getDebugExecutorInstance();
      ExecutionEnvironment env = new ExecutionEnvironment(ex, runner, configSettings, project);

      AndroidProjectSystem projectSystem = getProjectSystem(project);
      ApplicationIdProvider appIdProvider = projectSystem.getApplicationIdProvider(config);
      ApkProvider apkProvider = projectSystem.getApkProvider(config);

      LaunchOptions launchOptions = LaunchOptions.builder()
        .setClearLogcatBeforeStart(false)
        .setDebug(debug)
        .build();

      AndroidLaunchTasksProvider provider = new AndroidLaunchTasksProvider(
        (AndroidRunConfiguration)configSettings.getConfiguration(),
        env,
        androidFacet,
        appIdProvider,
        apkProvider,
        launchOptions);

      LaunchStatus launchStatus = mock(LaunchStatus.class);
      ConsolePrinter consolePrinter = mock(ConsolePrinter.class);

      // Act
      List<LaunchTask> launchTasks = provider.getTasks(device, launchStatus, consolePrinter);

      // Assert
      launchTasks.forEach(task -> Logger.getInstance(this.getClass()).info("LaunchTask: " + task));
      return Unit.INSTANCE;
    });
  }
}

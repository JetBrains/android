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
package com.android.tools.idea.run

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler
import com.android.tools.idea.model.TestExecutionOption
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

private const val TARGET_APP_ID = "target.application.packagename"
private const val ORCHESTRATOR_APP_ID = "android.support.test.orchestrator"
private const val ANDROIDX_ORCHESTRATOR_APP_ID = "androidx.test.orchestrator"

/**
 * Unit tests for [AndroidRunState].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class AndroidRunStateTest {
  @get:Rule
  val edtRule = EdtRule()
  @get:Rule
  val projectRule = ProjectRule()
  @get:Rule
  val disposableRule = DisposableRule()

  lateinit var env: ExecutionEnvironment

  @Mock
  lateinit var mockApplicationIdProvider: ApplicationIdProvider

  @Mock
  lateinit var mockConsoleProvider: ConsoleProvider

  @Mock
  lateinit var mockConsoleView: ConsoleView

  lateinit var deployTarget: DeployTarget

  @Mock
  lateinit var mockAndroidLaunchTasksProvider: AndroidLaunchTasksProvider

  @Mock
  lateinit var mockRunExecutor: Executor

  @Mock
  lateinit var mockProgramRunner: ProgramRunner<*>

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)

    whenever(mockApplicationIdProvider.packageName).thenReturn(TARGET_APP_ID)
    whenever(mockConsoleProvider.createAndAttach(any(), any(), any())).thenReturn(mockConsoleView)
    deployTarget = LaunchTaskRunnerTest.createDeployTarget(1)
  }

  @Test
  fun logcatShouldBeEnabledForApplicationRun() {
    val result = runAndroidApplication()

    val processHandler = result.processHandler
    assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
  }

  @Test
  fun logcatShouldBeDisabledForInstrumentationTestRun() {
    val result = runAndroidTestApplication()

    val processHandler = result.processHandler
    assertThat(processHandler).isInstanceOf(AndroidProcessHandler::class.java)
  }


  @Test
  fun androidProcessHandlerMonitorsMasterProcessId() {
    assertThat((runAndroidApplication().processHandler as AndroidProcessHandler).targetApplicationId).isEqualTo(TARGET_APP_ID)
    assertThat((runAndroidTestApplication(TestExecutionOption.HOST).processHandler as AndroidProcessHandler)
                 .targetApplicationId).isEqualTo(TARGET_APP_ID)
    assertThat((runAndroidTestApplication(TestExecutionOption.ANDROID_TEST_ORCHESTRATOR).processHandler as AndroidProcessHandler)
                 .targetApplicationId).isEqualTo(ORCHESTRATOR_APP_ID)
    assertThat((runAndroidTestApplication(TestExecutionOption.ANDROIDX_TEST_ORCHESTRATOR).processHandler as AndroidProcessHandler)
                 .targetApplicationId).isEqualTo(ANDROIDX_ORCHESTRATOR_APP_ID)
  }

  private fun runAndroidApplication(): ExecutionResult {
    val configSettings = RunManager.getInstance(projectRule.project).createConfiguration("run App", AndroidRunConfigurationType().factory)
    env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), AndroidConfigurationProgramRunner(), configSettings,
                               projectRule.project)

    val runState = AndroidRunState(env, "launch config name", projectRule.module, mockApplicationIdProvider,
                                   mockConsoleProvider, deployTarget, mockAndroidLaunchTasksProvider)
    return requireNotNull(runState.execute(mockRunExecutor, mockProgramRunner))
  }

  private fun runAndroidTestApplication(execution: TestExecutionOption = TestExecutionOption.HOST): ExecutionResult {
    val mockTestRunConfiguration = mock(AndroidTestRunConfiguration::class.java)
    whenever(mockTestRunConfiguration.getTestExecutionOption(nullable(AndroidFacet::class.java))).thenReturn(execution)
    whenever(mockTestRunConfiguration.name).thenReturn("app test")

    val configSettings = RunManager.getInstance(projectRule.project).createConfiguration("run test",
                                                                                         AndroidTestRunConfigurationType().factory!!)
    (configSettings as RunnerAndConfigurationSettingsImpl).setConfiguration(mockTestRunConfiguration)
    env = ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance(), AndroidConfigurationProgramRunner(), configSettings,
                               projectRule.project)

    val runState = AndroidRunState(env, "launch config name", projectRule.module, mockApplicationIdProvider,
                                   mockConsoleProvider, deployTarget, mockAndroidLaunchTasksProvider)
    return requireNotNull(runState.execute(mockRunExecutor, mockProgramRunner))
  }
}

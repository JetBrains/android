package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProject
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutorRunProfileState

import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetState
import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test

class GradleAndroidTestRunConfigurationExecutorProviderTest {

  @get:Rule
  val projectRule = AndroidProjectRule.testProject(object : LightGradleSyncTestProject {
    override val templateProject = AndroidCoreTestProject.SIMPLE_APPLICATION
    override val modelBuilders = listOf(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        selectedBuildVariant = "release",
        projectBuilder = AndroidProjectBuilder(
          namespace = { "google.simpleapplication" }
        ).build(),
      )
    )
  })

  @Test
  fun produceCorrectExecutor() {
    val config = object : AndroidTestRunConfiguration(projectRule.project, AndroidTestRunConfigurationType.getInstance().factory) {
      override fun getDeployTarget(): DeployTarget? {
        return object : DeployTarget {
          override fun hasCustomRunProfileState(executor: Executor) = false

          override fun getRunProfileState(executor: Executor, env: ExecutionEnvironment, state: DeployTargetState) = null

          override fun getDevices(project: Project) = DeviceFutures.forDevices(listOf(mock<IDevice>()))
        }
      }
    }
    config.setModule(projectRule.module)
    val env = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), config).build()
    val originalValue = AndroidTestConfiguration.getInstance().RUN_ANDROID_TEST_USING_GRADLE

    AndroidTestConfiguration.getInstance().RUN_ANDROID_TEST_USING_GRADLE = true
    val runProfileState = config.getState(DefaultRunExecutor.getRunExecutorInstance(), env) as AndroidConfigurationExecutorRunProfileState
    assertThat(runProfileState.executor).isInstanceOf(GradleAndroidTestRunConfigurationExecutor::class.java)

    AndroidTestConfiguration.getInstance().RUN_ANDROID_TEST_USING_GRADLE = false
    val runProfileState2 = config.getState(DefaultRunExecutor.getRunExecutorInstance(), env) as AndroidConfigurationExecutorRunProfileState
    assertThat(runProfileState2.executor).isInstanceOf(AndroidTestRunConfigurationExecutor::class.java)

    AndroidTestConfiguration.getInstance().RUN_ANDROID_TEST_USING_GRADLE = originalValue
  }
}
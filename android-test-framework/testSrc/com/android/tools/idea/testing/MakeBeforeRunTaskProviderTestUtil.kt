/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors
import com.android.tools.idea.gradle.run.MakeBeforeRunTask
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.AndroidConfigurationProgramRunner
import com.google.common.truth.Truth
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.runInEdtAndWait
import javax.swing.Icon

fun RunConfiguration.executeMakeBeforeRunStepInTest(device: IDevice) =
  executeMakeBeforeRunStepInTest(DeviceFutures.forDevices(listOf(device)))

fun RunConfiguration.executeMakeBeforeRunStepInTest(deviceFutures: DeviceFutures? = null) {
  val project = project
  val disposable = Disposer.newDisposable()

  // Make build failures visible in the test output.
  injectBuildOutputDumpingBuildViewManager(project, disposable)

  try {
    val makeBeforeRunTask = beforeRunTasks.filterIsInstance<MakeBeforeRunTask>().single()
    val factory = factory!!
    val runnerAndConfigurationSettings = RunManager.getInstance(project).createConfiguration(this, factory)

    // Set up ExecutionTarget infrastructure.
    ApplicationManager.getApplication().invokeAndWait {
      val target = object : AndroidExecutionTarget() {
        override fun getId(): String = "target"
        override fun getDisplayName(): String = "target"
        override fun getIcon(): Icon? = null
        override fun isApplicationRunning(packageName: String): Boolean = false
        override fun getAvailableDeviceCount(): Int = 1
        override fun getRunningDevices(): Collection<IDevice> = emptyList()
        override fun canRun(configuration: RunConfiguration): Boolean = configuration === this@executeMakeBeforeRunStepInTest
      }
      ExecutionTargetManager.getInstance(this.project).activeTarget = target
    }

    val programRunner = object : AndroidConfigurationProgramRunner() {
      override fun getRunnerId(): String = "runner_id"
      override fun canRunWithMultipleDevices(executorId: String): Boolean = false
      override val supportedConfigurationTypeIds = emptyList<String>()
      override fun run(environment: ExecutionEnvironment, state: RunProfileState, indicator: ProgressIndicator): RunContentDescriptor {
        return mock<RunContentDescriptor>()
      }
    }

    val executionEnvironment = ExecutionEnvironment(
      DefaultRunExecutor.getRunExecutorInstance(),
      programRunner,
      runnerAndConfigurationSettings,
      project
    )
    deviceFutures?.let { executionEnvironment.putCopyableUserData(DeviceFutures.KEY, deviceFutures) }
    try {
      Truth.assertThat(
        BeforeRunTaskProvider.getProvider(project, MakeBeforeRunTaskProvider.ID)!!
          .executeTask(
            DataContext.EMPTY_CONTEXT,
            this,
            executionEnvironment,
            makeBeforeRunTask
          )
      ).isTrue()
    } finally {
      runInEdtAndWait {
        AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
      }
    }
  } finally {
    Disposer.dispose(disposable)
  }
}

fun <T : RunConfiguration?> createRunConfigurationFromClass(
  project: Project,
  qualifiedName: String,
  expectedType: Class<T>
): T? {
  val element =
    JavaPsiFacade.getInstance(project)
      .findClass(qualifiedName, GlobalSearchScope.projectScope(project))
      ?.children?.firstOrNull { it is PsiIdentifier }
      ?: error("$qualifiedName class not found")

  val runConfiguration = createRunConfigurationFromPsiElement(project, element)
  return if (expectedType.isInstance(runConfiguration)) expectedType.cast(runConfiguration)
  else error("Wrong type of run configuration created: ${runConfiguration::class}")
}

private fun createContext(project: Project, psiElement: PsiElement): ConfigurationContext {
  val dataContext = MapDataContext()
  dataContext.put(CommonDataKeys.PROJECT, project)
  if (PlatformCoreDataKeys.MODULE.getData(dataContext) == null) {
    dataContext.put(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiElement))
  }
  dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiElement))
  return ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN)
}

private fun createRunConfigurationFromPsiElement(
  project: Project,
  psiElement: PsiElement
): RunConfiguration {
  val context = createContext(project, psiElement)
  val settings = context.configuration ?: return error("Failed to get/create run configuration settings")
  // Save the run configuration in the project.
  val runManager = RunManager.getInstance(project)
  runManager.addConfiguration(settings)
  return settings.configuration ?: error("Failed to create run configuration for: $psiElement")
}

@JvmOverloads
fun mockDeviceFor(androidVersion: Int, abis: List<Abi>, density: Int? = null): IDevice {
  val device = MockitoKt.mock<IDevice>()
  whenever(device.abis).thenReturn(abis.map { it.toString() })
  whenever(device.version).thenReturn(AndroidVersion(androidVersion))
  density?.let { whenever(device.density).thenReturn(density) }
  return device
}

fun withSimulatedSyncError(errorMessage: String, block: () -> Unit) {
  SimulatedSyncErrors.registerSyncErrorToSimulate(errorMessage)
  try {
    block()
  }
  finally {
    SimulatedSyncErrors.clear() // May leak to tests running afterwards.
  }
}

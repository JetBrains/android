/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.concurrency.scopeDisposable
import com.android.tools.idea.run.DeviceProvisionerAndroidDevice
import com.android.tools.idea.run.LaunchCompatibility
import com.android.tools.idea.run.LaunchCompatibilityChecker
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

internal open class Fixture(val project: Project, val testScope: TestScope) {
  /**
   * Create a child scope of the TestScope containing all scopes we create for this test, so that we
   * can cancel them easily when we're done. For the test to terminate properly, the test scope must
   * not have any remaining child jobs, but it must not be cancelled itself.
   */
  val scope = testScope.createChildScope()

  val runManager
    get() = RunManager.getInstance(project)

  val runConfiguration: RunnerAndConfigurationSettings = runManager.createTestConfig()
  val runConfigurationFlow = MutableStateFlow<RunnerAndConfigurationSettings?>(runConfiguration)

  val selectedTargetStateService
    get() = project.service<SelectedTargetStateService>()

  val devicesFlow = MutableStateFlow(emptyList<DeviceHandle>())
  val templatesFlow = MutableStateFlow(emptyList<DeviceTemplate>())
  val ddmlibDeviceLookupFlow =
    MutableStateFlow(mock<DeviceProvisionerAndroidDevice.DdmlibDeviceLookup>())
  val launchCompatibilityCheckerFlow = MutableSharedFlow<LaunchCompatibilityChecker>(replay = 1)
  val clock = TestClock()

  open val devicesService: DeploymentTargetDevicesService by lazy {
    DeploymentTargetDevicesService(
        scope,
        devicesFlow,
        templatesFlow,
        clock,
        ddmlibDeviceLookupFlow,
        launchCompatibilityCheckerFlow,
      )
      .also {
        project.replaceService(
          DeploymentTargetDevicesService::class.java,
          it,
          scope.scopeDisposable(),
        )
      }
  }

  suspend fun sendLaunchCompatibility() {
    launchCompatibilityCheckerFlow.emit(LaunchCompatibilityChecker { LaunchCompatibility.YES })
  }

  @OptIn(ExperimentalStdlibApi::class) // for CoroutineDispatcher
  open val devicesSelectedService: DevicesSelectedService by lazy {
    DevicesSelectedService(
        runConfigurationFlow,
        selectedTargetStateService,
        devicesService.loadedDevices,
        clock,
        testScope.coroutineContext[CoroutineDispatcher]!!,
      )
      .also {
        project.replaceService(DevicesSelectedService::class.java, it, scope.scopeDisposable())
        // Start collecting the lazy flow so that when we update its inputs, changes
        // appear immediately.
        scope.launch { it.devicesAndTargetsFlow.collect() }
      }
  }

  val comboBox by lazy {
    DeviceAndSnapshotComboBoxAction(
      { devicesService },
      { devicesSelectedService },
      Project::service,
      { runManager },
    )
  }

  open suspend fun runFixture(test: suspend () -> Unit) {
    try {
      test()
    } finally {
      Disposer.dispose(devicesSelectedService)
      scope.cancel()
    }
  }
}

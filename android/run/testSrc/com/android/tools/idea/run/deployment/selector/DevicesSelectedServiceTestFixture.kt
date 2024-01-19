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

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * Override the base fixture to replace DevicesService with a flow of Devices that we directly
 * control.
 */
internal class DevicesSelectedServiceTestFixture(project: Project, testScope: TestScope) :
  Fixture(project, testScope) {
  private val _devicesFlow = MutableStateFlow<List<DeploymentTargetDevice>>(emptyList())

  var devices: List<DeploymentTargetDevice>
    get() = _devicesFlow.value
    set(value) {
      _devicesFlow.value = value
      testScope.advanceUntilIdle()
    }

  override val devicesService: DeploymentTargetDevicesService
    get() = throw UnsupportedOperationException()

  @OptIn(ExperimentalStdlibApi::class)
  override val devicesSelectedService =
    DevicesSelectedService(
        runConfigurationFlow,
        selectedTargetStateService,
        _devicesFlow,
        clock,
        testScope.coroutineContext[CoroutineDispatcher]!!,
      )
      .also { scope.launch { it.devicesAndTargetsFlow.collect() } }
}

internal fun runTestWithDevicesSelectedServiceFixture(
  project: Project,
  block: suspend DevicesSelectedServiceTestFixture.() -> Unit,
) = runTest {
  with(DevicesSelectedServiceTestFixture(project, this@runTest)) { runFixture { block() } }
}

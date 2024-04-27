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
package com.android.tools.idea.execution.common.applychanges

import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService

fun setExecutionTargetForConfiguration(project: Project, device: IDevice?, config: RunConfiguration, testRootDisposable: Disposable) {
  val executionTarget = object : AndroidExecutionTarget() {
    override fun getId() = "Test Target"

    override fun getDisplayName() = "Test Target"

    override fun getIcon() = null

    override fun getAvailableDeviceCount() = 1

    override fun getRunningDevices() = if (device == null) emptyList() else listOf(device)
  }

  val executionTargetManager = MockitoKt.mock<ExecutionTargetManager>()
    .also {
      MockitoKt.whenever(it.activeTarget).thenReturn(executionTarget)
      MockitoKt.whenever(it.getTargetsFor(config)).thenReturn(listOf(executionTarget))
    }
  project.replaceService(ExecutionTargetManager::class.java, executionTargetManager, testRootDisposable)
}
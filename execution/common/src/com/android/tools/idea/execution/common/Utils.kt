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
package com.android.tools.idea.execution.common

import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.processhandler.DeviceAwareProcessHandler
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.impl.isOfSameType
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project


fun RunnerAndConfigurationSettings.getProcessHandlersForDevices(project: Project, devices: List<IDevice>): List<ProcessHandler> {
  return ExecutionManagerImpl.getInstance(project)
    .getRunningDescriptors { it.isOfSameType(this) }
    .mapNotNull { it.processHandler as? DeviceAwareProcessHandler }
    .filter { processHandler -> devices.any { processHandler.isAssociated(it) } }
}

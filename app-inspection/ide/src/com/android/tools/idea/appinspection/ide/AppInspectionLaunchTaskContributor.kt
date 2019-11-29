/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide

import com.android.ddmlib.IDevice
import com.android.tools.idea.appinspection.api.AutoPreferredProcess
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.run.AndroidLaunchTaskContributor
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTask
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.android.tools.idea.run.util.LaunchStatus
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.TransportService
import com.intellij.execution.Executor
import com.intellij.openapi.module.Module

class AppInspectionLaunchTaskContributor : AndroidLaunchTaskContributor {
  override fun getTask(module: Module, applicationId: String, launchOptions: LaunchOptions): LaunchTask {
    return if (StudioFlags.SQLITE_APP_INSPECTOR_ENABLED.get()) {
      AppInspectionLaunchTask(module)
    }
    else {
      AppInspectionStubTask()
    }
  }
}

private const val LAUNCH_TASK_ID = "app_inspection_discovery_id"

// Must be called only when SQLITE_APP_INSPECTOR_ENABLED is on
private class AppInspectionLaunchTask(private val module: Module) : LaunchTask {

  override fun getDescription() = "Connecting AppInspection"

  override fun getDuration() = LaunchTaskDurations.ASYNC_TASK

  override fun run(executor: Executor, device: IDevice, launchStatus: LaunchStatus, printer: ConsolePrinter): LaunchResult {
    val packageName = AndroidModuleInfo.getInstance(module)?.`package`

    AppInspectionHostService.instance.discoveryHost.connect(TransportFileManager(device, TransportService.getInstance().messageBus),
                                                            AutoPreferredProcess(
                                                              device,
                                                              packageName))
    return LaunchResult.success()
  }

  override fun getId() = LAUNCH_TASK_ID
}

private class AppInspectionStubTask : LaunchTask {
  override fun getDescription(): String {
    return "Stub task that does nothing"
  }

  override fun getDuration() = LaunchTaskDurations.ASYNC_TASK

  override fun run(executor: Executor, device: IDevice, launchStatus: LaunchStatus, printer: ConsolePrinter): LaunchResult {
    return LaunchResult.success()
  }

  override fun getId() = LAUNCH_TASK_ID
}
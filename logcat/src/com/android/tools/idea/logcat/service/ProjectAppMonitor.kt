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
package com.android.tools.idea.logcat.service

import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.common.ProcessEvent.ProcessRemoved
import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.PackageNamesProvider
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogcatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/**
 * Monitors project related processes start & end event and emits them as system log messages to a [Flow<List<LogcatMessage>>].
 */
internal class ProjectAppMonitor(
  private val processNameMonitor: ProcessNameMonitor,
  private val projectPackageNamesProvider: PackageNamesProvider
) {

  suspend fun monitorDevice(serialNumber: String): Flow<LogcatMessage> {
    val processes = mutableMapOf<Int, String>()
    return processNameMonitor.trackDeviceProcesses(serialNumber).transform {
      when (it) {
        is ProcessAdded -> {
          val applicationId = it.toProcessNames().applicationId
          if (applicationId in projectPackageNamesProvider.getPackageNames() && !processes.containsKey(it.pid)) {
            processes[it.pid] = applicationId
            emit(LogcatMessage(SYSTEM_HEADER, LogcatBundle.message("logcat.process.started", it.pid.toString(), applicationId)))
          }
        }

        is ProcessRemoved -> {
          val applicationId = processes.remove(it.pid) ?: return@transform
          emit(LogcatMessage(SYSTEM_HEADER, LogcatBundle.message("logcat.process.ended", it.pid.toString(), applicationId)))
        }
      }
    }
  }
}

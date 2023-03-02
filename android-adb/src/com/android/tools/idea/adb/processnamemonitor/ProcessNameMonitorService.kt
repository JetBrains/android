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
package com.android.tools.idea.adb.processnamemonitor

import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.processmonitor.monitor.ddmlib.AdbAdapterImpl
import com.android.processmonitor.monitor.ddmlib.ProcessNameMonitorDdmlib
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.AndroidAdbLogger
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/** A trivial [ProcessNameMonitor] that delegates to [ProcessNameMonitorDdmlib] */
internal class ProcessNameMonitorService(project: Project) : ProcessNameMonitor, Disposable {
  private val delegate = ProcessNameMonitorDdmlib(
    AndroidCoroutineScope(this),
    AdbLibService.getSession(project),
    AdbAdapterImpl(AdbService.getInstance().getDebugBridge(project)),
    StudioFlags.PROCESS_NAME_MONITOR_MAX_RETENTION.get(),
    AndroidAdbLogger(thisLogger()),
  )

  override fun start() = delegate.start()

  override fun getProcessNames(serialNumber: String, pid: Int) = delegate.getProcessNames(serialNumber, pid)

  override fun dispose() {
    delegate.close()
  }
}
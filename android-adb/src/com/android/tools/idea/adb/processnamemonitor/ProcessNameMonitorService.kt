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

import com.android.processmonitor.agenttracker.AgentProcessTrackerConfig
import com.android.processmonitor.agenttracker.AgentSourcePaths.AGENT_RESOURCE_PROD
import com.android.processmonitor.agenttracker.AgentSourcePaths.AGENT_SOURCE_DEV
import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.processmonitor.monitor.ProcessNameMonitorImpl
import com.android.processmonitor.monitor.ddmlib.AdbAdapterImpl
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.AndroidAdbLogger
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.downloads.AndroidProfilerDownloader
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path
import java.nio.file.Paths

private const val LOGCAT_PROTO_SUPPORT_SDK = 35

/** A trivial [ProcessNameMonitor] that delegates to [ProcessNameMonitorImpl] */
internal class ProcessNameMonitorService(project: Project) : ProcessNameMonitor, Disposable {
  private val delegate = let {
    val parentScope = AndroidCoroutineScope(this)
    val adbSession = AdbLibService.getSession(project)
    val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
    val adbLogger = AndroidAdbLogger(thisLogger())
    val adbAdapter = AdbAdapterImpl(AdbService.getInstance().getDebugBridge(project))
    val pollingIntervalMs = StudioFlags.PROCESS_NAME_TRACKER_AGENT_INTERVAL_MS.get()
    // If Logcat Proto format is supported and enabled, we don't need to use an agent.
    val shouldUseAgentForSdk: (Int) -> Boolean = { sdk -> sdk < LOGCAT_PROTO_SUPPORT_SDK || !StudioFlags.LOGCAT_PROTOBUF_ENABLED.get() }
    val trackerAgentConfig = when (StudioFlags.PROCESS_NAME_TRACKER_AGENT_ENABLE.get()) {
      true -> AgentProcessTrackerConfig(getAgentPath(), pollingIntervalMs, shouldUseAgentForSdk)
      false -> null
    }
    val config = ProcessNameMonitor.Config(StudioFlags.PROCESS_NAME_MONITOR_MAX_RETENTION.get(), trackerAgentConfig)

    when (StudioFlags.PROCESS_NAME_MONITOR_ADBLIB_ENABLED.get() && StudioFlags.ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER.get()) {
      true -> ProcessNameMonitorImpl.forAdblib(parentScope, adbSession, deviceProvisioner, config, adbLogger)
      false -> ProcessNameMonitorImpl.forDdmlib(parentScope, adbSession, adbAdapter, config, adbLogger)
    }
  }

  override fun start() = delegate.start()

  override fun getProcessNames(serialNumber: String, pid: Int) = delegate.getProcessNames(serialNumber, pid)

  override suspend fun trackDeviceProcesses(serialNumber: String): Flow<ProcessEvent> = delegate.trackDeviceProcesses(serialNumber)

  override fun dispose() {
    delegate.close()
  }

  private fun getAgentPath(): Path {
    return when {
      !IdeInfo.getInstance().isAndroidStudio -> AndroidProfilerDownloader.getInstance()
        .getHostDir("plugins/android/$AGENT_RESOURCE_PROD").toPath()

      StudioPathManager.isRunningFromSources() -> Paths.get(StudioPathManager.getBinariesRoot()).resolve(AGENT_SOURCE_DEV)
      else -> PluginPathManager.getPluginHome("android").toPath().resolve(AGENT_RESOURCE_PROD)
    }
  }
}
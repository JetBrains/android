/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection

import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.transport.FailedToStartServerException
import com.android.tools.idea.transport.TransportDeviceManager
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * Listener used to set the feature flag to true or false in the Transport Daemon.
 */
class ForegroundProcessDetectionOnDeviceFlagController : TransportDeviceManager.TransportDeviceManagerListener, ProjectManagerListener {

  override fun projectOpened(project: Project) {
    ApplicationManager.getApplication().messageBus.connect().subscribe(TransportDeviceManager.TOPIC, this)
  }

  override fun onPreTransportDaemonStart(device: Common.Device) { }
  override fun onTransportDaemonException(device: Common.Device, exception: Exception) { }
  override fun onTransportProxyCreationFail(device: Common.Device, exception: Exception) { }
  override fun onStartTransportDaemonServerFail(device: Common.Device, exception: FailedToStartServerException) { }

  override fun customizeProxyService(proxy: TransportProxy) { }
  override fun customizeAgentConfig(configBuilder: Agent.AgentConfig.Builder, runConfig: AndroidRunConfigurationBase?) { }

  override fun customizeDaemonConfig(configBuilder: Transport.DaemonConfig.Builder) {
    configBuilder
      .setLayoutInspectorConfig(
        configBuilder.layoutInspectorConfigBuilder.setAutoconnectEnabled(LayoutInspectorSettings.getInstance().autoConnectEnabled)
      )
  }
}
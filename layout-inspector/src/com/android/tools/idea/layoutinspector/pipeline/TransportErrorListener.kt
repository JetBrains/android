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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.transport.FailedToStartServerException
import com.android.tools.idea.transport.TransportDeviceManager
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

/**
 * Class responsible for listening to events published by the transport.
 */
class TransportErrorListener(private val project: Project) : TransportDeviceManager.TransportDeviceManagerListener {
  val errorMessage = LayoutInspectorBundle.message("two.versions.of.studio.running")

  private var hasStartServerFailed = false
    set(value) {
      field = value
      val bannerService = InspectorBannerService.getInstance(project)
      if (hasStartServerFailed) {
        // the banner can't be dismissed. It will automatically be dismissed when the Transport tries to start again.
        bannerService?.setNotification(errorMessage, emptyList())
        // TODO(b/258453315) log to metrics
      }
      else {
        bannerService?.removeNotification(errorMessage)
      }
    }

  init {
    project.messageBus.connect().subscribe(TransportDeviceManager.TOPIC, this)
  }

  override fun onPreTransportDaemonStart(device: Common.Device) {
    hasStartServerFailed = false
  }

  override fun onTransportDaemonException(device: Common.Device, exception: java.lang.Exception) { }

  override fun onTransportProxyCreationFail(device: Common.Device, exception: Exception) { }

  override fun onStartTransportDaemonServerFail(device: Common.Device, exception: FailedToStartServerException) {
    // this happens if the transport can't start the server on the designated port.
    // for example if multiple versions of Studio are running.
    hasStartServerFailed = true
  }

  override fun customizeProxyService(proxy: TransportProxy) { }
  override fun customizeDaemonConfig(configBuilder: Transport.DaemonConfig.Builder) { }
  override fun customizeAgentConfig(configBuilder: Agent.AgentConfig.Builder, runConfig: AndroidRunConfigurationBase?) { }
}
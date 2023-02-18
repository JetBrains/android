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

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.AppInspectionJarCopier
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.transport.DeployableFile
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportFileManager
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.TransportServiceProxy
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * This service holds a reference to [DefaultAppInspectionApiServices], which holds references to
 * [ProcessNotifier] and [InspectorLauncher]. The first is used to discover and track processes as
 * they come online. The latter is used to launch inspectors on discovered processes.
 */
@Service
class AppInspectionDiscoveryService : Disposable {
  init {
    // The following line has the side effect of starting the transport service if it has not been
    // already.
    // The consequence of not doing this is gRPC calls are never responded to.
    TransportService.getInstance()
  }

  private val client = TransportClient(TransportService.channelName)
  private val streamManager =
    TransportStreamManager.createManager(client.transportStub, AndroidDispatchers.workerThread)

  private val applicationMessageBus = ApplicationManager.getApplication().messageBus.connect(this)

  private val scope = AndroidCoroutineScope(this)

  val apiServices: AppInspectionApiServices =
    AppInspectionApiServices.createDefaultAppInspectionApiServices(
      client,
      streamManager,
      scope,
      // gRPC guarantees FIFO, so we want to poll gRPC messages sequentially
      MoreExecutors.newSequentialExecutor(AndroidExecutors.getInstance().workerThreadExecutor)
        .asCoroutineDispatcher()
    ) { device ->
      val jarCopier = findDevice(device)?.createJarCopier()
      if (jarCopier == null) {
        logger.error(
          AppInspectionBundle.message(
            "device.not.found",
            device.manufacturer,
            device.model,
            device.serial
          )
        )
      }
      jarCopier
    }

  init {
    applicationMessageBus.subscribe(
      ProjectManager.TOPIC,
      object : ProjectManagerListener {
        override fun projectClosing(project: Project) {
          scope.launch { apiServices.disposeClients(project.name) }
        }
      }
    )
  }

  private fun IDevice.createJarCopier(): AppInspectionJarCopier {
    return object : AppInspectionJarCopier {
      private val delegate =
        TransportFileManager(this@createJarCopier, TransportService.getInstance().messageBus)
      override fun copyFileToDevice(jar: AppInspectorJar): List<String> =
        delegate.copyFileToDevice(jar.toDeployableFile())
    }
  }

  companion object {
    private val logger = Logger.getInstance(AppInspectionDiscoveryService::class.java)
    val instance: AppInspectionDiscoveryService
      get() = service()
  }

  override fun dispose() {
    TransportStreamManager.unregisterManager(streamManager)
  }

  /**
   * This uses the current [AndroidDebugBridge] to locate a device described by [device]. Return
   * value is null if bridge is not available, bridge does not detect any devices, or if the
   * provided [device] does not match any of the devices the bridge is aware of.
   */
  private fun findDevice(device: DeviceDescriptor): IDevice? {
    return AndroidDebugBridge.getBridge()?.devices?.find {
      device.manufacturer == TransportServiceProxy.getDeviceManufacturer(it) &&
        device.model == TransportServiceProxy.getDeviceModel(it) &&
        device.serial == it.serialNumber
    }
  }

  private fun AppInspectorJar.toDeployableFile() =
    DeployableFile.Builder(name)
      .apply {
        releaseDirectory?.let { this.setReleaseDir(it) }
        developmentDirectory?.let { this.setDevDir(it) }
      }
      .build()
}

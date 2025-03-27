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

import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.transport.FailedToStartServerException
import com.android.tools.idea.transport.TransportDeviceManager
import com.android.tools.profiler.proto.Common
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorTransportError
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TWO_VERSIONS_RUNNING_KEY = "two.versions.of.studio.running"

/** Class responsible for listening to events published by the transport. */
class TransportErrorListener(
  project: Project,
  private val notificationModel: NotificationModel,
  private val layoutInspectorMetrics: LayoutInspectorMetrics,
  disposable: Disposable,
  scope: CoroutineScope,
) : TransportDeviceManager.TransportDeviceManagerListener {

  private var hasStartServerFailed = false

  init {
    project.messageBus.connect(disposable).subscribe(TransportDeviceManager.TOPIC, this)
    // Because the transport only attempts to start the server once, this notification is kept in
    // the loop. This ensures that if the server fails to start, the user will continue to see the
    // error banner, even if they restart the layout inspector multiple times.
    scope.launch {
      while (isActive) {
        if (hasStartServerFailed) {
          notificationModel.addNotification(
            TWO_VERSIONS_RUNNING_KEY,
            LayoutInspectorBundle.message(TWO_VERSIONS_RUNNING_KEY),
            Status.Error,
            emptyList(),
          )
        } else {
          notificationModel.removeNotification(TWO_VERSIONS_RUNNING_KEY)
        }
        delay(500)
      }
    }
  }

  override fun onPreTransportDaemonStart(device: Common.Device) {
    hasStartServerFailed = false
  }

  override fun onTransportDaemonException(device: Common.Device, exception: java.lang.Exception) {}

  override fun onTransportProxyCreationFail(device: Common.Device, exception: Exception) {}

  override fun onStartTransportDaemonServerFail(
    device: Common.Device,
    exception: FailedToStartServerException,
  ) {
    // this happens if the transport can't start the server on the designated port.
    // for example if multiple versions of Studio are running.
    hasStartServerFailed = true
    layoutInspectorMetrics.logTransportError(
      DynamicLayoutInspectorTransportError.Type.TRANSPORT_FAILED_TO_START_DAEMON,
      device.toDeviceDescriptor(),
    )
  }
}

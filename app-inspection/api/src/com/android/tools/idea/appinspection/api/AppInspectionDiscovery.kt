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
package com.android.tools.idea.appinspection.api

import com.android.tools.idea.appinspection.internal.AppInspectionAttacher
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.transport.TransportFileCopier
import com.google.common.util.concurrent.FutureCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

typealias TargetListener = (AppInspectionTarget) -> Unit

/**
 * A class that hosts an [AppInspectionDiscovery] instance and exposes a method to initialize its connection.
 *
 * Ideally, a central host service in the calling code will create and connect this discovery and then share just
 * the discovery with interested clients.
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
class AppInspectionDiscoveryHost(executor: ScheduledExecutorService, channel: Channel) {
  /**
   * This class represents a channel between some host (which should implement this class) and a target Android device.
   */
  interface Channel {
    val name: String
  }

  val discovery = AppInspectionDiscovery(executor, channel)

  // TODO(b/143836794): this method should return to the caller if it was to connect to device
  fun connect(transportFileCopier: TransportFileCopier, preferredProcess: AutoPreferredProcess) {
    discovery.connect(transportFileCopier, preferredProcess)
  }
}

/**
 * A class which listens for [AppInspectionTarget] instances when they become available.
 *
 * Note that even if there are multiple devices, this class is designed so that a single discovery instance could be connected
 * to and fire listeners for all of them.
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
class AppInspectionDiscovery(private val executor: ScheduledExecutorService,
                             private val channel: AppInspectionDiscoveryHost.Channel) {
  private val listeners = ConcurrentHashMap<TargetListener, Executor>()
  private val attacher = AppInspectionAttacher(executor, channel)

  internal fun connect(fileCopier: TransportFileCopier, preferredProcess: AutoPreferredProcess) {
    attacher.attach(preferredProcess) { stream, process ->
      AppInspectionTarget.attach(stream, process, channel.name, executor, fileCopier)
        .addCallback(executor, object : FutureCallback<AppInspectionTarget> {
          override fun onSuccess(result: AppInspectionTarget?) {
            listeners.forEach {
              it.value.execute { it.key(result!!) }
            }
          }

          override fun onFailure(t: Throwable) {
            TODO("not implemented")
          }
        })

    }
  }

  fun addTargetListener(executor: Executor, listener: TargetListener): TargetListener {
    listeners[listener] = executor
    return listener
  }
}

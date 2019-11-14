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

import com.android.tools.idea.appinspection.api.AppInspectionDiscovery
import com.android.tools.idea.appinspection.api.AppInspectionDiscoveryHost
import com.android.tools.idea.transport.TransportService
import com.intellij.openapi.components.ServiceManager
import com.intellij.util.concurrency.AppExecutorUtil

// service that holds a reference to [AppInspectionDiscoveryHost] and has full access to it: it has power to establish new connections
internal class AppInspectionHostService {
  private val transportChannel = object : AppInspectionDiscoveryHost.TransportChannel {
    override val channelName = TransportService.getInstance().channelName
  }

  val discoveryHost = AppInspectionDiscoveryHost(
    AppExecutorUtil.getAppScheduledExecutorService(), transportChannel)

  companion object {
    val instance: AppInspectionHostService
      get() = ServiceManager.getService(AppInspectionHostService::class.java)
  }
}

// "class" is used simply as a namespace, an object by itself must be stateless. It is purpose is to provide public access to subset
// of AppInspectionHostService API: clients can add a listener to be notified about new connections, but can't establish new one.
object AppInspectionClientsService {
  val discovery: AppInspectionDiscovery
    get() = AppInspectionHostService.instance.discoveryHost.discovery
}


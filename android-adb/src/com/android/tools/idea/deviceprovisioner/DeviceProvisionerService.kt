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
package com.android.tools.idea.deviceprovisioner

import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.createChildScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * A project service that instantiates and provides access to the [DeviceProvisioner] and its
 * plugins.
 */
@Service(Service.Level.PROJECT)
class DeviceProvisionerService(private val project: Project) : Disposable {

  // Construction is lazy to avoid registering the coroutineScope as a child Disposable
  // of this service before this service has its own parent. If construction aborts in that
  // case, the service will be retained as a child of the root disposable and leaked.
  val deviceProvisioner: DeviceProvisioner by lazy {
    val session = AdbLibService.getSession(project)
    val coroutineScope =
      session.scope.createChildScope(isSupervisor = true, parentDisposable = this)

    DeviceProvisioner.create(
      coroutineScope,
      session,
      DeviceProvisionerFactory.createProvisioners(coroutineScope, project),
    )
  }

  override fun dispose() {}
}

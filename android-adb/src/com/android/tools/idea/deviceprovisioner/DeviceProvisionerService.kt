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
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * A project service that instantiates and provides access to the [DeviceProvisioner] and its
 * plugins.
 */
@Service
class DeviceProvisionerService(project: Project) {
  val deviceProvisioner =
    AdbLibService.getSession(project).let { session ->
      DeviceProvisioner.create(
        session,
        DeviceProvisionerFactory.createProvisioners(session.scope, project)
      )
    }
}

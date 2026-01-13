/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Cancel and wait for the DeviceProvisioner Job after a test is finished. This avoids the job to
 * execute after FakeAdbServerAdbLibRule or FakeAdbServerRule has stopped the FakeAdbServer. If the
 * DeviceProvisioner is allowed to continue after FakeAdbServer is stopped it may lead to uncaught
 * coroutine exceptions which may be reported in unrelated tests using TestScope.runTest. See
 * ExceptionCollector.addOnExceptionCallback.
 */
class DeviceProvisionerServiceCleanUpRule(private val projectProvider: () -> Project) :
  ExternalResource() {
  private lateinit var deviceProvisioner: DeviceProvisioner

  override fun before() {
    val service: DeviceProvisionerService = projectProvider().service()
    deviceProvisioner = service.deviceProvisioner
  }

  override fun after() {
    runBlocking { deviceProvisioner.scope.coroutineContext[Job]?.cancelAndJoin() }
  }
}

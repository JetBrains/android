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
package com.android.tools.idea.adb

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.registerServiceInstance
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import org.junit.rules.ExternalResource
import org.mockito.Mockito

import java.io.File

class FakeAdbServiceRule(
  private val projectSupplier: () -> Project,
  private val adbRule: FakeAdbRule
) : ExternalResource() {
  private var serverKilled = false
  private var serviceDisposable: Disposable? = null

  init {
    // Ensure we use the protocol compliant `sync` handler. We can remove this when it is the default.
    adbRule.withDeviceCommandHandler(SyncCommandHandler())
  }

  override fun before() {
    val adbFile: File = MockitoKt.mock()
    val bridge: AndroidDebugBridge = Mockito.spy(adbRule.bridge)
    val disposable = Disposer.newDisposable().also { serviceDisposable = it }
    val adbFileProvider = AdbFileProvider { adbFile }
    projectSupplier().registerServiceInstance(AdbFileProvider::class.java, adbFileProvider, disposable)
    val service: AdbService = MockitoKt.mock()
    ApplicationManager.getApplication().replaceService(AdbService::class.java, service, disposable)
    Mockito.doAnswer {
      serverKilled = false
      Futures.immediateFuture(bridge)
    }.whenever(service).getDebugBridge(MockitoKt.eq(adbFile))
    Mockito.doAnswer {
      if (serverKilled) {
        error("Server was killed. Do not keep instances of AndroidDebugBridge around.")
      }
      adbRule.bridge.devices
    }.whenever(bridge).devices
  }

  override fun after() {
    serviceDisposable?.let { Disposer.dispose(it) }
  }

  /**
   * Imitate that the adb server was killed.
   *
   * This can be used in tests to ensure that [AndroidDebugBridge.getDevices] are not called on a stale bridge instance.
   */
  fun killServer() {
    serverKilled = true
  }
}

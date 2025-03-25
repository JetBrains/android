/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.testing.FakeAdbRule
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.adb.AdbService
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import java.io.File
import org.junit.rules.ExternalResource
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Rule for making AdbUtils.getAdbFuture(Project) return AdbRule.bridge. */
class AdbServiceRule(private val projectSupplier: () -> Project, private val adbRule: FakeAdbRule) :
  ExternalResource() {
  private var serverKilled = false
  private var serviceDisposable: Disposable? = null

  override fun before() {
    val adbFile: File = mock()
    val bridge: AndroidDebugBridge = spy(adbRule.bridge)
    val disposable = Disposer.newDisposable().also { serviceDisposable = it }
    val adbFileProvider = AdbFileProvider { adbFile }
    projectSupplier().replaceService(AdbFileProvider::class.java, adbFileProvider, disposable)
    val service: AdbService = mock()
    ApplicationManager.getApplication().replaceService(AdbService::class.java, service, disposable)
    whenever(service.getDebugBridge(adbFile)).thenAnswer {
      serverKilled = false
      Futures.immediateFuture(bridge)
    }
    whenever(bridge.devices).thenAnswer {
      if (serverKilled) {
        error("Server was killed. Do not keep instances of AndroidDebugBridge around.")
      }
      adbRule.bridge.devices
    }
  }

  override fun after() {
    serviceDisposable?.let { Disposer.dispose(it) }
  }

  /**
   * Imitate that the adb server was killed.
   *
   * This can be used in tests to ensure that [AndroidDebugBridge.getDevices] are not called on a
   * stale bridge instance.
   */
  fun killServer() {
    serverKilled = true
  }
}
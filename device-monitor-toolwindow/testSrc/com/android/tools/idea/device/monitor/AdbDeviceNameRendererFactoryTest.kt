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
package com.android.tools.idea.device.monitor

import com.android.tools.idea.adb.AdbService
import com.android.tools.idea.ddms.DeviceNameProperties
import com.android.tools.idea.device.monitor.adbimpl.AdbDeviceListService.Companion.getInstance
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher
import com.android.tools.idea.device.monitor.adbimpl.AdbDeviceNameRendererFactory
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.util.concurrent.FutureCallback
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdbDeviceNameRendererFactoryTest {
  @get:Rule
  val androidProjectRule = AndroidProjectRule.withSdk()

  private val project: Project
    get() = androidProjectRule.project

  private lateinit var parentDisposable: Disposable

  @Before
  fun setup() {
    parentDisposable = Disposer.newDisposable()
  }

  @After
  fun tearDown() {
    Disposer.dispose(parentDisposable)
    try {
      // We need this call so that we don't leak a thread (the ADB Monitor thread)
      AdbService.getInstance().terminateDdmlib()
    }
    catch (e: Throwable) {
      e.printStackTrace()
    }
  }

  @Throws(Exception::class)
  @Test
  fun testCreateMethodWorks() {
    // Prepare
    val service = getInstance(project)
    val factory = AdbDeviceNameRendererFactory(service)
    val fetcher = DeviceNamePropertiesFetcher(parentDisposable, object : FutureCallback<DeviceNameProperties> {
      override fun onSuccess(result: DeviceNameProperties?) {}
      override fun onFailure(t: Throwable) {}
    })

    // Act
    val renderer = factory.create(fetcher)

    // Assert
    TestCase.assertNotNull(renderer.nameRenderer)
  }
}
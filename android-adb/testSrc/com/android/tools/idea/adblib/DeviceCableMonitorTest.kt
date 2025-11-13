/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.idea.adblib

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidApiLevel
import com.android.tools.adblib.testutils.FakeAdbServerAdbLibRule
import com.intellij.facet.impl.FacetUtil
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.ProjectRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class DeviceCableMonitorTest {
  private val projectRule = ProjectRule()
  private val adbRule = FakeAdbServerAdbLibRule()

  private lateinit var monitor: DeviceCableMonitor

  private val latch = CountDownLatch(1)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(adbRule)!!

  @Before
  fun setup() {
    projectRule.project.messageBus
      .connect()
      .subscribe(
        Notifications.TOPIC,
        object : Notifications {
          override fun notify(notification: Notification) {
            if (notification.groupId == DeviceCableMonitor.NOTIFICATION_GROUP_ID) {
              latch.countDown()
            }
          }
        },
      )
  }

  @Test
  fun badUSBCableNotificationTest() = runBlockingWithTimeout {
    // Add devices with desired properties here through adbRule.
    adbRule.connectDevice(
      deviceId = "device_with_bad_usb_cable",
      manufacturer = "Google",
      deviceModel = "Pixel7",
      release = "10.0.0",
      sdk = AndroidApiLevel(34),
      hostConnectionType = DeviceState.HostConnectionType.USB,
      maxSpeedMbps = 5000L,
      negotiatedSpeedMbps = 480L,
    )

    WriteAction.runAndWait<Throwable> {
      FacetUtil.addFacet(projectRule.module, AndroidFacet.getFacetType())
    }

    monitor = DeviceCableMonitor()
    CoroutineScope(Dispatchers.IO).launch { monitor.execute(projectRule.project) }

    if (!latch.await(5, TimeUnit.SECONDS)) {
      Assert.fail("Bad cable notification not detected")
    }
  }
}
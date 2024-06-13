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
package com.android.tools.idea.appinspection.ide.ui

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

val MODERN_DEVICE =
  object : DeviceDescriptor {
    override val manufacturer = "Google"
    override val model = "Modern Model"
    override val serial = "123456"
    override val isEmulator = false
    override val apiLevel = AndroidVersion.VersionCodes.Q
    override val version = "Q"
    override val codename: String? = null
  }

val LEGACY_DEVICE =
  object : DeviceDescriptor by MODERN_DEVICE {
    override val model = "Legacy Model"
    override val serial = "123"
    override val apiLevel = AndroidVersion.VersionCodes.M
    override val version = "M"
  }

fun DeviceDescriptor.createProcess(
  name: String = "com.example",
  packageName: String = name,
  pid: Int = 1,
  streamId: Long = 13579,
  isRunning: Boolean = true,
): ProcessDescriptor {
  val device = this
  return object : ProcessDescriptor {
    override val device = device
    override val abiCpuArch = "x86_64"
    override val name = name
    override val packageName = packageName
    override val isRunning = isRunning
    override val pid = pid
    override val streamId = streamId
  }
}

class RecentProcessTest {
  @Test
  fun testPreferredProcessMatchesModernDeviceOnly() {
    val p1 = RecentProcess("123456", "p1")
    assertThat(p1.matches(MODERN_DEVICE.createProcess("p1"))).isTrue()
    assertThat(p1.matches(LEGACY_DEVICE.createProcess("p1"))).isFalse()
    assertThat(p1.matches(MODERN_DEVICE.createProcess("p2"))).isFalse()
  }

  @Test
  fun testPreferredProcessMatchesLegacyDeviceOnly() {
    val p2 = RecentProcess("123", "p2")
    assertThat(p2.matches(LEGACY_DEVICE.createProcess("p2"))).isTrue()
    assertThat(p2.matches(MODERN_DEVICE.createProcess("p2"))).isFalse()
    assertThat(p2.matches(LEGACY_DEVICE.createProcess("p1"))).isFalse()
  }

  @Test
  fun testPreferredProcessMatchesByPackageName() {
    val p1 = RecentProcess("123456", "package1")

    assertThat(p1.matches(MODERN_DEVICE.createProcess("process1", "package1"))).isTrue()
    assertThat(p1.matches(MODERN_DEVICE.createProcess("process2", "package1"))).isTrue()
    assertThat(p1.matches(MODERN_DEVICE.createProcess("process1", "package2"))).isFalse()
  }
}

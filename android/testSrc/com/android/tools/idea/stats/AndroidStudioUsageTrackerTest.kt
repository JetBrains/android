/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.ddmlib.IDevice
import com.android.tools.analytics.HostData.graphicsEnvironment
import com.android.tools.analytics.HostData.osBean
import com.android.tools.analytics.stubs.StubGraphicsDevice.Companion.withBounds
import com.android.tools.analytics.stubs.StubGraphicsEnvironment
import com.android.tools.analytics.stubs.StubOperatingSystemMXBean
import com.android.tools.idea.stats.AndroidStudioUsageTracker.buildActiveExperimentList
import com.android.tools.idea.stats.AndroidStudioUsageTracker.deviceToDeviceInfo
import com.android.tools.idea.stats.AndroidStudioUsageTracker.deviceToDeviceInfoApilLevelOnly
import com.android.tools.idea.stats.AndroidStudioUsageTracker.getMachineDetails
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DisplayDetails
import com.google.wireless.android.sdk.stats.MachineDetails
import junit.framework.TestCase
import org.easymock.EasyMock
import org.junit.Assert
import java.awt.GraphicsDevice
import java.io.File

class AndroidStudioUsageTrackerTest : TestCase() {
  fun testDeviceToDeviceInfo() {
    val info = deviceToDeviceInfo(createMockDevice())
    assertEquals(info.anonymizedSerialNumber, AnonymizerUtil.anonymizeUtf8("serial"))
    assertEquals(info.buildTags, "release-keys")
    assertEquals(info.buildType, "userdebug")
    assertEquals(info.buildVersionRelease, "5.1.1")
    assertEquals(info.buildApiLevelFull, "24")
    assertEquals(info.cpuAbi, DeviceInfo.ApplicationBinaryInterface.X86_ABI)
    assertEquals(info.manufacturer, "Samsung")
    assertTrue(info.deviceType == DeviceInfo.DeviceType.LOCAL_PHYSICAL)
    assertEquals(info.model, "pixel")
  }

  fun testDeviceToDeviceInfoApilLevelOnly() {
    val info = deviceToDeviceInfoApilLevelOnly(createMockDevice())
    // Test only Api Level is set
    assertEquals(info.buildApiLevelFull, "24")
    assertEquals(info.anonymizedSerialNumber, "")
  }

  fun testGetMachineDetails() {
    // Use the file root to get a consistent disk size
    // (we normally use the studio install path).
    val root = File(File.separator)
    // Stub out the Operating System MX Bean to get consistent system info in the test.
    osBean = object : StubOperatingSystemMXBean() {
      override fun getAvailableProcessors(): Int {
        return 16
      }

      override fun getTotalPhysicalMemorySize(): Long {
        return 16L * 1024 * 1024 * 1024
      }
    }

    // Stub out the Graphics Environment to get consistent screen sizes in the test.
    graphicsEnvironment = object : StubGraphicsEnvironment() {
      override fun getScreenDevices(): Array<GraphicsDevice> {
        return arrayOf(
          withBounds(640, 480),
          withBounds(1024, 768)
        )
      }

      override fun isHeadlessInstance(): Boolean {
        return false
      }
    }
    try {
      val expected = MachineDetails.newBuilder()
        .setAvailableProcessors(16)
        .setTotalRam(16L * 1024 * 1024 * 1024)
        .setTotalDisk(root.totalSpace)
        .addDisplay(DisplayDetails.newBuilder().setWidth(640).setHeight(480).setSystemScale(1.0f))
        .addDisplay(DisplayDetails.newBuilder().setWidth(1024).setHeight(768).setSystemScale(1.0f))
        .build()
      val result = getMachineDetails(root)
      Assert.assertEquals(expected, result)
    }
    finally {
      // undo the stubbing of Operating System MX Bean.
      osBean = null
      // undo the stubbing of Graphics Environment.
      graphicsEnvironment = null
    }
  }

  fun testBuildActiveExperimentList() {
    try {
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "")
      Truth.assertThat(buildActiveExperimentList()).hasSize(0)
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "single")
      Truth.assertThat(buildActiveExperimentList()).containsExactly("single")
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "one,two")
      Truth.assertThat(buildActiveExperimentList()).containsExactly("one", "two")
    }
    finally {
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "")
    }
  }

  companion object {
    fun createMockDevice(): IDevice {
      val mockDevice = EasyMock.createMock<IDevice>(IDevice::class.java)
      EasyMock.expect(mockDevice.serialNumber).andStubReturn("serial")
      EasyMock.expect(mockDevice.getProperty(IDevice.PROP_BUILD_TAGS)).andStubReturn("release-keys")
      EasyMock.expect(mockDevice.getProperty(IDevice.PROP_BUILD_TYPE)).andStubReturn("userdebug")
      EasyMock.expect(mockDevice.getProperty(IDevice.PROP_BUILD_VERSION)).andStubReturn("5.1.1")
      EasyMock.expect(mockDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL)).andStubReturn("24")
      EasyMock.expect(mockDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI)).andStubReturn("x86")
      EasyMock.expect(mockDevice.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)).andStubReturn("Samsung")
      EasyMock.expect(mockDevice.isEmulator).andStubReturn(java.lang.Boolean.FALSE)
      EasyMock.expect(mockDevice.getProperty(IDevice.PROP_DEVICE_MODEL)).andStubReturn("pixel")
      EasyMock.replay(mockDevice)
      return mockDevice
    }
  }
}
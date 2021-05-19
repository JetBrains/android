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
package com.android.tools.idea.stats;

import static com.android.tools.idea.stats.AndroidStudioUsageTracker.getMachineDetails;

import com.android.ddmlib.IDevice;
import com.android.tools.analytics.HostData;
import com.android.tools.analytics.stubs.StubGraphicsDevice;
import com.android.tools.analytics.stubs.StubGraphicsEnvironment;
import com.android.tools.analytics.stubs.StubOperatingSystemMXBean;
import com.google.common.truth.Truth;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.google.wireless.android.sdk.stats.DisplayDetails;
import com.google.wireless.android.sdk.stats.MachineDetails;
import java.awt.GraphicsDevice;
import java.awt.HeadlessException;
import java.io.File;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

public class AndroidStudioUsageTrackerTest extends TestCase {

  static IDevice createMockDevice() {
    IDevice mockDevice = EasyMock.createMock(IDevice.class);
    EasyMock.expect(mockDevice.getSerialNumber()).andStubReturn("serial");
    EasyMock.expect(mockDevice.getProperty(IDevice.PROP_BUILD_TAGS)).andStubReturn("release-keys");
    EasyMock.expect(mockDevice.getProperty(IDevice.PROP_BUILD_TYPE)).andStubReturn("userdebug");
    EasyMock.expect(mockDevice.getProperty(IDevice.PROP_BUILD_VERSION)).andStubReturn("5.1.1");
    EasyMock.expect(mockDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL)).andStubReturn("24");
    EasyMock.expect(mockDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI)).andStubReturn("x86");
    EasyMock.expect(mockDevice.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)).andStubReturn("Samsung");
    EasyMock.expect(mockDevice.isEmulator()).andStubReturn(Boolean.FALSE);
    EasyMock.expect(mockDevice.getProperty(IDevice.PROP_DEVICE_MODEL)).andStubReturn("pixel");
    EasyMock.replay(mockDevice);
    return mockDevice;
  }

  public void testDeviceToDeviceInfo() throws Exception {
    DeviceInfo info = AndroidStudioUsageTracker.deviceToDeviceInfo(createMockDevice());
    assertEquals(info.getAnonymizedSerialNumber(), AnonymizerUtil.anonymizeUtf8("serial"));
    assertEquals(info.getBuildTags(), "release-keys");
    assertEquals(info.getBuildType(), "userdebug");
    assertEquals(info.getBuildVersionRelease(), "5.1.1");
    assertEquals(info.getBuildApiLevelFull(), "24");
    assertEquals(info.getCpuAbi(), DeviceInfo.ApplicationBinaryInterface.X86_ABI);
    assertEquals(info.getManufacturer(), "Samsung");
    assertTrue(info.getDeviceType() == DeviceInfo.DeviceType.LOCAL_PHYSICAL);
    assertEquals(info.getModel(), "pixel");
  }

  public void testDeviceToDeviceInfoApilLevelOnly() throws Exception {
    DeviceInfo info = AndroidStudioUsageTracker.deviceToDeviceInfoApilLevelOnly(createMockDevice());
    // Test only Api Level is set
    assertEquals(info.getBuildApiLevelFull(), "24");

    assertEquals(info.getAnonymizedSerialNumber(), "");
  }

  public void testGetMachineDetails() {
    // Use the file root to get a consistent disk size
    // (we normally use the studio install path).
    File root = new File(File.separator);
    // Stub out the Operating System MX Bean to get consistent system info in the test.
    HostData.setOsBean(new StubOperatingSystemMXBean() {
      @Override
      public int getAvailableProcessors() {
        return 16;
      }

      @Override
      public long getTotalPhysicalMemorySize() {
        return 16L * 1024 * 1024 * 1024;
      }
    });

    // Stub out the Graphics Environment to get consistent screen sizes in the test.
    HostData.setGraphicsEnvironment(new StubGraphicsEnvironment() {
      @NotNull
      @Override
      public GraphicsDevice[] getScreenDevices() throws HeadlessException {
        return new GraphicsDevice[]{StubGraphicsDevice.withBounds(640, 480),
          StubGraphicsDevice.withBounds(1024, 768)};
      }

      @Override
      public boolean isHeadlessInstance() {
        return false;
      }
    });

    try {
      MachineDetails expected = MachineDetails.newBuilder()
        .setAvailableProcessors(16)
        .setTotalRam(16L * 1024 * 1024 * 1024)
        .setTotalDisk(root.getTotalSpace())
        .addDisplay(DisplayDetails.newBuilder().setWidth(640).setHeight(480).setSystemScale(1.0f))
        .addDisplay(DisplayDetails.newBuilder().setWidth(1024).setHeight(768).setSystemScale(1.0f))
        .build();
      MachineDetails result = getMachineDetails(root);
      Assert.assertEquals(expected, result);
    }
    finally {
      // undo the stubbing of Operating System MX Bean.
      HostData.setOsBean(null);
      // undo the stubbing of Graphics Environment.
      HostData.setGraphicsEnvironment(null);
    }
  }

  public void testBuildActiveExperimentList() {
    try {
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "");
      Truth.assertThat(AndroidStudioUsageTracker.buildActiveExperimentList()).hasSize(0);

      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "single");
      Truth.assertThat(AndroidStudioUsageTracker.buildActiveExperimentList()).containsExactly("single");

      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "one,two");
      Truth.assertThat(AndroidStudioUsageTracker.buildActiveExperimentList()).containsExactly("one", "two");
    } finally {
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "");
    }
  }

}
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

import com.android.ddmlib.IDevice;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.junit.Test;

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

  @Test
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

  @Test
  public void testDeviceToDeviceInfoApilLevelOnly() throws Exception {
    DeviceInfo info = AndroidStudioUsageTracker.deviceToDeviceInfoApilLevelOnly(createMockDevice());
    // Test only Api Level is set
    assertEquals(info.getBuildApiLevelFull(), "24");

    assertEquals(info.getAnonymizedSerialNumber(), "");
  }
}
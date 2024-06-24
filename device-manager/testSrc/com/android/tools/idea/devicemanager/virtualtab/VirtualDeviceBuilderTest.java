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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.sdklib.internal.avd.ConfigKey;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.devicemanager.Device;
import com.android.tools.idea.devicemanager.DeviceType;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceBuilderTest {
  @Test
  public void noProperties() {
    AvdInfo avdInfo = new AvdInfo(Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  null,
                                  null,
                                  AvdStatus.OK);

    VirtualDevice device = new VirtualDeviceBuilder(avdInfo, () -> false, () -> 0L).build();

    assertEquals(0, device.getSizeOnDisk());
    assertNull(device.getResolution());
    assertNull(device.getDp());
  }

  @Test
  public void noHeight() {
    Map<String, String> properties = new HashMap<>();
    properties.put("hw.lcd.width", "1080");
    properties.put("hw.lcd.density", "440");

    AvdInfo avdInfo = new AvdInfo(Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  null,
                                  AvdStatus.OK);

    Device device = new VirtualDeviceBuilder(avdInfo, () -> false, () -> 1_024L).build();

    assertNull(device.getResolution());
    assertNull(device.getDp());
  }

  @Test
  public void invalidResolution() {
    Map<String, String> properties = new HashMap<>();
    properties.put("hw.lcd.width", "not a number");
    properties.put("hw.lcd.height", "2160");
    properties.put("hw.lcd.density", "not a number");

    AvdInfo avdInfo = new AvdInfo(Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  null,
                                  AvdStatus.OK);

    VirtualDevice device = new VirtualDeviceBuilder(avdInfo, () -> false, () -> 1_024L).build();

    assertEquals(1_024, device.getSizeOnDisk());
    assertNull(device.getResolution());
    assertNull(device.getDp());
  }

  @Test
  public void wearOsTag() {
    Map<String, String> properties = new HashMap<>();
    properties.put(ConfigKey.TAG_ID, "android-wear");

    AvdInfo avdInfo = new AvdInfo(Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  null,
                                  AvdStatus.OK);

    Device device = new VirtualDeviceBuilder(avdInfo, () -> false, () -> 1_024L).build();

    assertEquals(DeviceType.WEAR_OS, device.getType());
  }

  @Test
  public void androidTvTag() {
    Map<String, String> properties = new HashMap<>();
    properties.put(ConfigKey.TAG_ID, "android-tv");

    AvdInfo avdInfo = new AvdInfo(Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  null,
                                  AvdStatus.OK);

    Device device = new VirtualDeviceBuilder(avdInfo, () -> false, () -> 1_024L).build();

    assertEquals(DeviceType.TV, device.getType());
  }

  @Test
  public void googleTvTag() {
    Map<String, String> properties = new HashMap<>();
    properties.put(ConfigKey.TAG_ID, "google-tv");

    AvdInfo avdInfo = new AvdInfo(Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  null,
                                  AvdStatus.OK);

    Device device = new VirtualDeviceBuilder(avdInfo, () -> false, () -> 1_024L).build();

    assertEquals(DeviceType.TV, device.getType());
  }

  @Test
  public void automotiveTag() {
    Map<String, String> properties = new HashMap<>();
    properties.put(ConfigKey.TAG_ID, "android-automotive");

    AvdInfo avdInfo = new AvdInfo(Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  null,
                                  AvdStatus.OK);

    Device device = new VirtualDeviceBuilder(avdInfo, () -> false, () -> 1_024L).build();

    assertEquals(DeviceType.AUTOMOTIVE, device.getType());
  }

  @Test
  public void automotivePlayTag() {
    Map<String, String> properties = new HashMap<>();
    properties.put(ConfigKey.TAG_ID, "android-automotive-playstore");

    AvdInfo avdInfo = new AvdInfo(Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  null,
                                  AvdStatus.OK);

    Device device = new VirtualDeviceBuilder(avdInfo, () -> false, () -> 1_024L).build();

    assertEquals(DeviceType.AUTOMOTIVE, device.getType());
  }
}

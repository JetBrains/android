/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DevicePickerListModelTest {
  private DevicePickerListModel myModel;

  @Before
  public void setUp() {
    myModel = new DevicePickerListModel();
  }

  @Test
  public void testEmptyList() {
    myModel.reset(Collections.<IDevice>emptyList(), Collections.<AvdInfo>emptyList());
    List<DevicePickerEntry> items = myModel.getItems();
    assertEquals("When there are no devices, an info message and an empty message should be shown", 2, items.size());
    assertTrue(items.get(0).isMarker());
    assertTrue(items.get(1).isMarker());
  }

  @Test
  public void testAvds() {
    myModel.reset(Collections.<IDevice>emptyList(), Arrays.asList(createMockAvd("avd1"), createMockAvd("avd2")));

    List<DevicePickerEntry> items = myModel.getItems();
    assertEquals("Expected 5 items (separator, troubleshoot info, separator, avd1, avd2)", 5, items.size());

    // Note: ordering established by AndroidDeviceComparator
    assertEquals("avd1", items.get(3).getAndroidDevice().getName());
    assertEquals("avd2", items.get(4).getAndroidDevice().getName());
  }

  @Test
  public void testConnectedDevice() {
    IDevice device1 = createMockDevice(false, null, 23, "6.0");
    myModel.reset(Collections.singletonList(device1), Collections.<AvdInfo>emptyList());

    List<DevicePickerEntry> items = myModel.getItems();
    assertEquals("Expected 2 items (separator, device1)", 2, items.size());

    assertTrue(items.get(0).isMarker());
    assertNotNull(items.get(1).getAndroidDevice());
  }

  @Test
  public void testRunningAvdsAreNotDuplicated() {
    IDevice device = createMockDevice(false, null, 23, "6.0");
    IDevice runningAvd = createMockDevice(true, "emu1", 22, "5.0");
    AvdInfo emu1Info = createMockAvd("emu1");
    AvdInfo emu2Info = createMockAvd("emu2");

    myModel.reset(Arrays.asList(device, runningAvd), Arrays.asList(emu1Info, emu2Info));

    List<DevicePickerEntry> items = myModel.getItems();
    assertEquals("Expected 5 items (marker, 2 devices, marker, 1 avd info)", 5, items.size());

    assertEquals("emu2", items.get(4).getAndroidDevice().getName());
  }

  @NotNull
  private static IDevice createMockDevice(boolean isEmulator, @Nullable String avdName, int apiLevel, @NotNull String buildVersion) {
    IDevice device = mock(IDevice.class);
    when(device.isEmulator()).thenReturn(isEmulator);
    when(device.getAvdName()).thenReturn(avdName);
    when(device.getVersion()).thenReturn(new AndroidVersion(apiLevel, buildVersion));
    return device;
  }

  @NotNull
  private static AvdInfo createMockAvd(@NotNull String avdName) {
    Map<String, String> properties = Maps.newHashMap();
    return new AvdInfo(avdName, new File("ini"), "folder", null, properties);
  }
}

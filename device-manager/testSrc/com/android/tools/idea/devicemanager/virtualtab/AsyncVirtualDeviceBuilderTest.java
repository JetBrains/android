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
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.devicemanager.DeviceType;
import com.android.tools.idea.devicemanager.Resolution;
import com.android.tools.idea.devicemanager.TestDeviceManagerFutures;
import com.google.common.util.concurrent.Futures;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class AsyncVirtualDeviceBuilderTest {
  @Test
  public void noProperties() throws Exception {
    AvdInfo avdInfo = new AvdInfo("Pixel_3_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  null,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(0L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertEquals(0, device.getSizeOnDisk());
    assertNull(device.getResolution());
    assertNull(device.getDp());
  }

  @Test
  public void noHeight() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put("hw.lcd.width", "1080");
    properties.put("hw.lcd.density", "440");

    AvdInfo avdInfo = new AvdInfo("Pixel_3_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(1_024L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertNull(device.getResolution());
    assertNull(device.getDp());
  }

  @Test
  public void invalidResolution() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put("hw.lcd.width", "not a number");
    properties.put("hw.lcd.height", "2160");
    properties.put("hw.lcd.density", "not a number");

    AvdInfo avdInfo = new AvdInfo("Pixel_3_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(1_024L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertEquals(1_024, device.getSizeOnDisk());
    assertNull(device.getResolution());
    assertNull(device.getDp());
  }

  @Test
  public void withSizeAndResolution() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put("hw.lcd.width", "1080");
    properties.put("hw.lcd.height", "2160");
    properties.put("hw.lcd.density", "440");

    AvdInfo avdInfo = new AvdInfo("Pixel_3_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(1_024L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertEquals(1_024, device.getSizeOnDisk());
    assertEquals(new Resolution(1_080, 2_160), device.getResolution());
    assertEquals(new Resolution(393, 786), device.getDp());
  }

  @Test
  public void wearOsTag() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put(AvdManager.AVD_INI_TAG_ID, "android-wear");

    AvdInfo avdInfo = new AvdInfo("Wear_OS_Round_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(1_024L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertEquals(DeviceType.WEAR_OS, device.getType());
  }

  @Test
  public void androidTvTag() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put(AvdManager.AVD_INI_TAG_ID, "android-tv");

    AvdInfo avdInfo = new AvdInfo("Android_TV_1080p_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(1_024L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertEquals(DeviceType.TV, device.getType());
  }

  @Test
  public void googleTvTag() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put(AvdManager.AVD_INI_TAG_ID, "google-tv");

    AvdInfo avdInfo = new AvdInfo("Google_TV_1080p_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(1_024L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertEquals(DeviceType.TV, device.getType());
  }

  @Test
  public void automotiveTag() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put(AvdManager.AVD_INI_TAG_ID, "android-automotive");

    AvdInfo avdInfo = new AvdInfo("Automotive_1024p_landscape_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(1_024L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertEquals(DeviceType.AUTOMOTIVE, device.getType());
  }

  @Test
  public void automotivePlayTag() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put(AvdManager.AVD_INI_TAG_ID, "android-automotive-playstore");

    AvdInfo avdInfo = new AvdInfo("Automotive_1024p_landscape_API_30",
                                  Paths.get("ini", "file"),
                                  Paths.get("data", "folder", "path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    AsyncVirtualDeviceBuilder builder = new AsyncVirtualDeviceBuilder(avdInfo, Futures.immediateFuture(1_024L));

    Future<VirtualDevice> future = builder.buildAsync();
    VirtualDevice device = TestDeviceManagerFutures.get(future);

    assertEquals(DeviceType.AUTOMOTIVE, device.getType());
  }
}

/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import junit.framework.TestCase;

public class DevicePropertyUtilTest extends TestCase {
  public void testManufacturerName() {
    assertEquals("HTC", DevicePropertyUtil.fixManufacturerName("htc"));
    assertEquals("Sony Ericsson", DevicePropertyUtil.fixManufacturerName("sony ericsson"));
    assertEquals("Samsung", DevicePropertyUtil.fixManufacturerName("samsung"));
  }

  public void testGetBuild() {
    assertEquals("Android 8.0.0, API 26", DevicePropertyUtil.getBuild("8.0.0", null, "26"));
    assertEquals("Android 8.0.1, API 27", DevicePropertyUtil.getBuild("8.0.1", "REL", "27"));
    assertEquals("Android 7.7", DevicePropertyUtil.getBuild("7.7", null, null));
    assertEquals("Android 7.8.9", DevicePropertyUtil.getBuild("7.8.9", "REL", null));
    assertEquals("Android P, API Q", DevicePropertyUtil.getBuild("P", "Q", "27"));
    assertEquals(", API 25", DevicePropertyUtil.getBuild(null, null, "25"));
    assertEquals(", API P", DevicePropertyUtil.getBuild(null, "P", "25"));
    assertEquals("", DevicePropertyUtil.getBuild(null, null, null));
  }
}

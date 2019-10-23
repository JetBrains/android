/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.ddms.DeviceNameProperties;
import org.junit.Test;

public final class ConnectedDeviceTest {
  @Test
  public void getNameManufacturerAndModelAreNull() {
    assertEquals("Unknown Device", ConnectedDevice.getName(new DeviceNameProperties(null, null, null, null)));
  }

  @Test
  public void getNameManufacturerIsNull() {
    assertEquals("Nexus 5X", ConnectedDevice.getName(new DeviceNameProperties("Nexus 5X", null, null, null)));
  }

  @Test
  public void getNameModelIsNull() {
    assertEquals("LGE Device", ConnectedDevice.getName(new DeviceNameProperties(null, "LGE", null, null)));
  }

  @Test
  public void getName() {
    assertEquals("LGE Nexus 5X", ConnectedDevice.getName(new DeviceNameProperties("Nexus 5X", "LGE", null, null)));
  }
}

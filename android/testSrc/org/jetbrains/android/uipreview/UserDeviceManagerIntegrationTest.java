/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.uipreview;

import org.jetbrains.android.AndroidTestCase;

public class UserDeviceManagerIntegrationTest extends AndroidTestCase {
  public void testReadDeviceXml() {
    myFixture.copyFileToProject("devices/devices_one_device.xml", "devices.xml");
    UserDeviceManager manager = UserDeviceManager.getInstanceWithCustomPath(getProject(), myFixture.getTempDirPath());
    assertEquals(1, manager.getUserDevices().size());
  }

  public void testNoDeviceXml() {
    UserDeviceManager manager = UserDeviceManager.getInstanceWithCustomPath(getProject(), myFixture.getTempDirPath());
    assertEquals(0, manager.getUserDevices().size());
  }
}
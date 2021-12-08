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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceDetailsPanel.SummarySection;
import com.android.tools.idea.wearpairing.WearPairingManager;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceDetailsPanelTest {
  @Test
  public void newVirtualDeviceDetailsPanel() {
    Map<String, String> properties = new HashMap<>();
    properties.put(AvdManager.AVD_INI_ANDROID_API, "30");
    properties.put("hw.lcd.width", "1080");
    properties.put("hw.lcd.height", "2160");
    properties.put("hw.lcd.density", "440");

    AvdInfo avdInfo = new AvdInfo("Pixel_3_API_30",
                                  Paths.get("ini/file"),
                                  Paths.get("data/folder/path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.OK);

    VirtualDeviceDetailsPanel panel = new VirtualDeviceDetailsPanel(VirtualDevices.build(avdInfo, device -> false),
                                                                    WearPairingManager.INSTANCE);
    SummarySection section = panel.getSummarySection();

    assertEquals("30", section.myApiLevelLabel.getText());
    assertEquals("1080 × 2160", section.myResolutionLabel.getText());
    assertEquals("393 × 786", section.myDpLabel.getText());
    assertNull(section.myErrorLabel);
    assertNull(section.mySnapshotLabel);
  }

  @Test
  public void newVirtualDeviceDetailsPanelError() {
    Map<String, String> properties = new HashMap<>();
    properties.put(AvdManager.AVD_INI_ANDROID_API, "30");
    properties.put(AvdManager.AVD_INI_TAG_ID, "google_apis_playstore");
    properties.put(AvdManager.AVD_INI_TAG_DISPLAY, "Google Play");
    properties.put(AvdManager.AVD_INI_ABI_TYPE, "x86");
    properties.put("hw.lcd.width", "1080");
    properties.put("hw.lcd.height", "2160");
    properties.put("hw.lcd.density", "440");

    AvdInfo avdInfo = new AvdInfo("Pixel_3_API_30",
                                  Paths.get("ini/file"),
                                  Paths.get("data/folder/path"),
                                  Mockito.mock(SystemImage.class),
                                  properties,
                                  AvdStatus.ERROR_IMAGE_MISSING);

    VirtualDeviceDetailsPanel panel = new VirtualDeviceDetailsPanel(VirtualDevices.build(avdInfo, device -> false),
                                                                    WearPairingManager.INSTANCE);
    SummarySection section = panel.getSummarySection();

    assertEquals("30", section.myApiLevelLabel.getText());
    assertEquals("1080 × 2160", section.myResolutionLabel.getText());
    assertEquals("393 × 786", section.myDpLabel.getText());
    assertNotNull(section.myErrorLabel);
    assertEquals("Missing system image for Google Play x86 Pixel_3_API_30.", section.myErrorLabel.getText());
    assertNull(section.mySnapshotLabel);
  }
}

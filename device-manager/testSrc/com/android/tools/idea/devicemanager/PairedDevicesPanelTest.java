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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceName;
import com.android.tools.idea.wearpairing.ConnectionState;
import com.android.tools.idea.wearpairing.PairingDevice;
import com.android.tools.idea.wearpairing.WearPairingManager;
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair;
import com.intellij.openapi.Disposable;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.AbstractButton;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PairedDevicesPanelTest {
  private final PairingDevice myPixel4Api31 = new PairingDevice("Pixel_4_API_31",
                                                                "Pixel 4 API 31",
                                                                31,
                                                                true,
                                                                false,
                                                                true,
                                                                ConnectionState.DISCONNECTED);

  private final PairingDevice myWearOsSmallRoundApi28 = new PairingDevice("Wear_OS_Small_Round_API_28",
                                                                          "Wear OS Small Round API 28",
                                                                          28,
                                                                          true,
                                                                          true,
                                                                          true,
                                                                          ConnectionState.DISCONNECTED);

  private final @NotNull WearPairingManager myManager = Mockito.mock(WearPairingManager.class);

  @Test
  public void removeButtonDoesntThrowExceptionWhenSelectionIsEmpty() {
    // Arrange
    Mockito.when(myManager.getPairsForDevice("Pixel_4_API_31"))
      .thenReturn(Collections.singletonList(new PhoneWearPair(myPixel4Api31, myWearOsSmallRoundApi28)));

    PairedDevicesPanel panel = new PairedDevicesPanel(new VirtualDeviceName("Pixel_4_API_31"),
                                                      Mockito.mock(Disposable.class),
                                                      null,
                                                      myManager);

    AbstractButton button = panel.getRemoveButton();

    // Act
    button.doClick();
  }

  @Test
  public void reloadPairingsAdd() {
    // Arrange
    Mockito.when(myManager.getPairsForDevice("Pixel_4_API_31"))
      .thenReturn(Collections.emptyList())
      .thenReturn(Collections.singletonList(new PhoneWearPair(myPixel4Api31, myWearOsSmallRoundApi28)));

    PairedDevicesPanel panel = new PairedDevicesPanel(new VirtualDeviceName("Pixel_4_API_31"),
                                                      Mockito.mock(Disposable.class),
                                                      null,
                                                      myManager);

    // Act
    panel.reloadPairings();

    // Assert
    Object device = new DeviceManagerPairingDevice.Builder()
      .setKey(new VirtualDeviceName("Wear_OS_Small_Round_API_28"))
      .setType(DeviceType.WEAR_OS)
      .setIcon(DeviceType.WEAR_OS.getVirtualIcon())
      .setName("Wear OS Small Round API 28")
      .setTarget("Android 9.0")
      .setAndroidVersion(new AndroidVersion(28))
      .build();

    assertEquals(Collections.singletonList(Arrays.asList(device, "Unknown")), TestTables.getData(panel.getTable()));
  }

  @Test
  public void reloadPairingsRemove() {
    Mockito.when(myManager.getPairsForDevice("Pixel_4_API_31"))
      .thenReturn(Collections.singletonList(new PhoneWearPair(myPixel4Api31, myWearOsSmallRoundApi28)))
      .thenReturn(Collections.emptyList());

    PairedDevicesPanel panel = new PairedDevicesPanel(new VirtualDeviceName("Pixel_4_API_31"),
                                                      Mockito.mock(Disposable.class),
                                                      null,
                                                      myManager);

    // Act
    panel.reloadPairings();

    // Assert
    assertEquals(Collections.emptyList(), TestTables.getData(panel.getTable()));
  }
}

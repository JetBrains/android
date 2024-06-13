/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.ui;

import com.android.sdklib.devices.Device;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import java.util.Comparator;
import org.jetbrains.annotations.NotNull;

/**
 * Sorts names so they appear in the device definition table in the following order
 *
 * <ol>
 *   <li>Small Phone
 *   <li>Medium Phone
 *   <li>Medium Tablet
 *   <li>The other devices (in reversed natural order)
 *   <li>Pixel XL
 *   <li>Pixel
 *   <li>7.6" Fold-in with outer display
 *   <li>Resizable (Experimental)
 * </ol>
 */
@SuppressWarnings("GrazieInspection")
final class NameComparator implements Comparator<Device> {
  @NotNull
  private static final Comparator<Device> COMPARATOR = Comparator.comparing(SortKey::valueOfDevice)
    .thenComparing(Device::getDisplayName, Collator.getInstance(ULocale.ROOT).reversed())
    .thenComparing(Device::getId);

  private enum SortKey {
    SMALL_PHONE,
    MEDIUM_PHONE,
    MEDIUM_TABLET,
    DEVICE,
    PIXEL_XL,
    PIXEL,
    SEVEN_POINT_SIX_INCH_FOLD_IN_WITH_OUTER_DISPLAY,
    RESIZABLE_EXPERIMENTAL;

    @NotNull
    private static SortKey valueOfDevice(@NotNull Device device) {
      return switch (device.getDisplayName()) {
        case "Small Phone" -> SMALL_PHONE;
        case "Medium Phone" -> MEDIUM_PHONE;
        case "Medium Tablet" -> MEDIUM_TABLET;
        case "Pixel XL" -> PIXEL_XL;
        case "Pixel" -> PIXEL;
        case "7.6\" Fold-in with outer display" -> SEVEN_POINT_SIX_INCH_FOLD_IN_WITH_OUTER_DISPLAY;
        case "Resizable (Experimental)" -> RESIZABLE_EXPERIMENTAL;
        default -> DEVICE;
      };
    }
  }

  @Override
  public int compare(@NotNull Device device1, @NotNull Device device2) {
    return COMPARATOR.compare(device1, device2);
  }
}

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
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import java.util.Comparator;
import java.util.function.BooleanSupplier;
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
 *   <li>Resizable (Experimental)
 * </ol>
 */
@SuppressWarnings("GrazieInspection")
final class NameComparator implements Comparator<Device> {
  private final @NotNull Comparator<@NotNull Device> myComparator;

  NameComparator() {
    this(StudioFlags.RESIZABLE_EXPERIMENTAL_TWEAKS_ENABLED::get);
  }

  @VisibleForTesting
  NameComparator(@NotNull BooleanSupplier resizableExperimentalTweaksEnabledGet) {
    myComparator = Comparator.<Device, SortKey>comparing(device -> SortKey.valueOfDevice(device, resizableExperimentalTweaksEnabledGet))
      .thenComparing(Device::getDisplayName, Collator.getInstance(ULocale.ROOT).reversed())
      .thenComparing(Device::getId);
  }

  private enum SortKey {
    SMALL_PHONE,
    MEDIUM_PHONE,
    MEDIUM_TABLET,
    DEVICE,
    PIXEL_XL,
    PIXEL,
    RESIZABLE_EXPERIMENTAL;

    private static @NotNull SortKey valueOfDevice(@NotNull Device device, @NotNull BooleanSupplier resizableExperimentalTweaksEnabledGet) {
      return switch (device.getDisplayName()) {
        case "Small Phone" -> SMALL_PHONE;
        case "Medium Phone" -> MEDIUM_PHONE;
        case "Medium Tablet" -> MEDIUM_TABLET;
        case "Pixel XL" -> PIXEL_XL;
        case "Pixel" -> PIXEL;
        case "Resizable (Experimental)" -> resizableExperimentalTweaksEnabledGet.getAsBoolean() ? DEVICE : RESIZABLE_EXPERIMENTAL;
        default -> DEVICE;
      };
    }
  }

  @Override
  public int compare(@NotNull Device device1, @NotNull Device device2) {
    return myComparator.compare(device1, device2);
  }
}

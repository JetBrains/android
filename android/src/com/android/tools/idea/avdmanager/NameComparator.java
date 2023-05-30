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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.devices.Device;
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
 *   <li>Resizable (Experimental)
 * </ol>
 */
@SuppressWarnings("GrazieInspection")
final class NameComparator implements Comparator<Device> {
  @NotNull
  private static final Comparator<String> COMPARATOR = Comparator.comparing(SortKey::valueOfDeviceName)
    .thenComparing(Comparator.naturalOrder());

  private enum SortKey {
    RESIZABLE_EXPERIMENTAL,
    DEVICE,
    MEDIUM_TABLET,
    MEDIUM_PHONE,
    SMALL_PHONE;

    @NotNull
    private static SortKey valueOfDeviceName(@NotNull String deviceName) {
      return switch (deviceName) {
        case "Resizable (Experimental)" -> RESIZABLE_EXPERIMENTAL;
        case "Medium Tablet" -> MEDIUM_TABLET;
        case "Medium Phone" -> MEDIUM_PHONE;
        case "Small Phone" -> SMALL_PHONE;
        default -> DEVICE;
      };
    }
  }

  @Override
  public int compare(@NotNull Device device1, @NotNull Device device2) {
    var name1 = device1.getDisplayName();
    assert !name1.isEmpty();

    var name2 = device2.getDisplayName();
    assert !name2.isEmpty();

    return COMPARATOR.compare(name1, name2);
  }
}

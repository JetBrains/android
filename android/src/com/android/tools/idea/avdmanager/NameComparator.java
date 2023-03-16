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
 * This comparator special cases the canonical device definitions ("Small Phone", "Medium Phone", and "Medium Tablet") to sort before other
 * names in the device definition table. After that, names that start with a letter sort before names that do not (as before). And after
 * that we use natural string order (as before).
 */
final class NameComparator implements Comparator<Device> {
  @NotNull
  private static final Comparator<String> COMPARATOR = Comparator.comparing(SortKey::valueOfDeviceName)
    .thenComparing(Comparator.naturalOrder());

  private enum SortKey {
    FIRST_CHAR_ISNT_LETTER,
    FIRST_CHAR_IS_LETTER,
    MEDIUM_TABLET,
    MEDIUM_PHONE,
    SMALL_PHONE;

    @NotNull
    private static SortKey valueOfDeviceName(@NotNull String deviceName) {
      return switch (deviceName) {
        case "Medium Tablet" -> MEDIUM_TABLET;
        case "Medium Phone" -> MEDIUM_PHONE;
        case "Small Phone" -> SMALL_PHONE;
        default -> Character.isLetter(deviceName.charAt(0)) ? FIRST_CHAR_IS_LETTER : FIRST_CHAR_ISNT_LETTER;
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

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
package com.android.tools.idea.devicemanager.physicaltab;

import com.google.common.annotations.VisibleForTesting;
import com.ibm.icu.number.LocalizedNumberFormatter;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.util.MeasureUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StorageDevice {
  private static final @NotNull Pattern PATTERN = Pattern.compile(".+\\s+\\d+\\s+\\d+\\s+(\\d+)\\s+.+\\s+.+");

  private static final @NotNull LocalizedNumberFormatter FORMATTER = NumberFormatter.withLocale(Locale.US)
    .unit(MeasureUnit.MEGABYTE);

  /**
   * In megabytes (1,024 × 1,024 bytes)
   */
  private final int myAvailableSpace;

  @VisibleForTesting
  StorageDevice(int availableSpace) {
    myAvailableSpace = availableSpace;
  }

  static @NotNull Optional<@NotNull StorageDevice> newStorageDevice(@NotNull List<@NotNull String> output) {
    // In 1,024 byte blocks
    OptionalInt availableSpace = Patterns.parseInt(PATTERN, output.get(1));

    if (!availableSpace.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(new StorageDevice(availableSpace.getAsInt() / 1_024));
  }

  @Override
  public int hashCode() {
    return myAvailableSpace;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof StorageDevice && myAvailableSpace == ((StorageDevice)object).myAvailableSpace;
  }

  @Override
  public @NotNull String toString() {
    return FORMATTER.format(myAvailableSpace).toString();
  }
}

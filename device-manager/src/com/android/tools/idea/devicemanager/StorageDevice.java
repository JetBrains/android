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

import com.google.common.annotations.VisibleForTesting;
import com.ibm.icu.number.LocalizedNumberFormatter;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.util.MeasureUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StorageDevice {
  private static final @NotNull Pattern PATTERN = Pattern.compile(".+\\s+\\d+\\s+\\d+\\s+(\\d+)\\s+.+\\s+.+");

  private static final @NotNull LocalizedNumberFormatter FORMATTER = NumberFormatter.withLocale(Locale.US)
    .unit(MeasureUnit.MEGABYTE);

  /**
   * In megabytes (1,024 × 1,024 bytes)
   */
  private final int myAvailableSpace;

  @VisibleForTesting
  public StorageDevice(int availableSpace) {
    myAvailableSpace = availableSpace;
  }

  public static @NotNull Optional<StorageDevice> newStorageDevice(@NotNull List<String> output) {
    // In 1,024 byte blocks
    return Patterns.parseInt(PATTERN, output.get(1)).stream()
      .map(availableSpace -> availableSpace / 1_024)
      .mapToObj(StorageDevice::new)
      .findFirst();
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

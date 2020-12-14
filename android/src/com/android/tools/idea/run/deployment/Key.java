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
package com.android.tools.idea.run.deployment;

import org.jetbrains.annotations.NotNull;

/**
 * A device identifier. The {@link #toString} output is what gets persisted in the
 * {@link com.intellij.ide.util.PropertiesComponent PropertiesComponent.}
 */
public abstract class Key implements Comparable<Key> {
  static @NotNull Key newKey(@NotNull String string) {
    if (string.startsWith(VirtualDevicePath.PREFIX)) {
      return VirtualDevicePath.parse(string);
    }

    if (string.startsWith(VirtualDeviceName.PREFIX)) {
      return VirtualDeviceName.parse(string);
    }

    if (string.startsWith(SerialNumber.PREFIX)) {
      return SerialNumber.parse(string);
    }

    return new NonprefixedKey(string);
  }

  /**
   * If this or the other key is nonprefixed, returns true if both values are equal. Otherwise returns true if both keys are equal.
   *
   * <p>We want a NonprefixedKey("Pixel_4_API_30") from DevicesSelectedService to match a VirtualDeviceName("Pixel_4_API_30") from
   * VirtualDevicesTask even though they're not strictly equal according to the equals method
   *
   * <p>When no users have persisted nonprefixed keys, this should be replaced with regular equals comparisons
   */
  boolean matches(@NotNull Key key) {
    return key instanceof NonprefixedKey ? asNonprefixedKey().equals(key) : equals(key);
  }

  abstract @NotNull NonprefixedKey asNonprefixedKey();

  @Override
  public final int compareTo(@NotNull Key key) {
    return toString().compareTo(key.toString());
  }
}

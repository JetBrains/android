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
      return new VirtualDevicePath(string.substring(VirtualDevicePath.PREFIX.length()));
    }

    if (string.startsWith(VirtualDeviceName.PREFIX)) {
      return new VirtualDeviceName(string.substring(VirtualDeviceName.PREFIX.length()));
    }

    if (string.startsWith(SerialNumber.PREFIX)) {
      return new SerialNumber(string.substring(SerialNumber.PREFIX.length()));
    }

    return new NonprefixedKey(string);
  }

  boolean matches(@NotNull Key key) {
    return key instanceof NonprefixedKey ? asNonprefixedKey().equals(key) : equals(key);
  }

  abstract @NotNull NonprefixedKey asNonprefixedKey();

  /**
   * @return the device part of a key. Keys may refer to a device and snapshot pair.
   */
  abstract @NotNull String getDeviceKey();

  @Override
  public final int compareTo(@NotNull Key key) {
    return toString().compareTo(key.toString());
  }
}

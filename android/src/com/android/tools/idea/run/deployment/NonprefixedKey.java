/*
 * Copyright (C) 2020 The Android Open Source Project
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
import org.jetbrains.annotations.Nullable;

/**
 * Either a virtual device name returned by IDevice.getAvdName or a serial number returned by IDevice.getSerialNumber.
 * DevicesSelectedService wrote them as is, without a prefix (hence nonprefixed); now it writes them with "VirtualDeviceName@", etc prefixes
 * ("VirtualDeviceName@Pixel_4_API_30") to better ensure that virtual device names aren't compared with serial numbers.
 */
final class NonprefixedKey extends Key {
  private final @NotNull String myValue;

  NonprefixedKey(@NotNull String value) {
    myValue = value;
  }

  @Override
  boolean matches(@NotNull Key key) {
    return myValue.equals(key.asNonprefixedKey().myValue);
  }

  @Override
  @NotNull NonprefixedKey asNonprefixedKey() {
    return this;
  }

  @Override
  @NotNull String getDeviceKey() {
    return myValue;
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof NonprefixedKey && myValue.equals(((NonprefixedKey)object).myValue);
  }

  @Override
  public @NotNull String toString() {
    return myValue;
  }
}

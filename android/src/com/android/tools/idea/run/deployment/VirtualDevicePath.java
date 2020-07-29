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
 * A virtual device path returned by AvdInfo.getDataFolderPath and IDevice.getAvdPath
 */
final class VirtualDevicePath extends Key {
  static final String PREFIX = "VirtualDevicePath@";
  private final @NotNull String myValue;

  VirtualDevicePath(@NotNull String value) {
    myValue = value;
  }

  @Override
  @NotNull NonprefixedKey asNonprefixedKey() {
    return new NonprefixedKey(myValue);
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
    return object instanceof VirtualDevicePath && myValue.equals(((VirtualDevicePath)object).myValue);
  }

  @Override
  public @NotNull String toString() {
    return PREFIX + myValue;
  }
}

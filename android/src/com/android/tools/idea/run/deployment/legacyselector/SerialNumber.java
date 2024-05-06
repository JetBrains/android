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
package com.android.tools.idea.run.deployment.legacyselector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A serial number returned by IDevice.getSerialNumber. These are usually used for physical devices but are also used for virtual devices
 * when IDevice.getAvdPath returns null and when IDevice.getAvdName returns null or "&lt;build&gt;". The AVD Manager won't know about these
 * virtual devices.
 */
public final class SerialNumber extends Key {
  private final @NotNull String myValue;

  public SerialNumber(@NotNull String value) {
    myValue = value;
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof SerialNumber && myValue.equals(((SerialNumber)object).myValue);
  }

  @Override
  public @NotNull String toString() {
    return myValue;
  }
}

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

import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A virtual device path returned by AvdInfo.getDataFolderPath and IDevice.getAvdPath. These are the primary virtual device identifiers and
 * should be used over virtual device names and serial numbers if possible.
 */
public final class VirtualDevicePath extends Key {
  @NotNull
  private final Object myValue;

  public VirtualDevicePath(@NotNull Path value) {
    myValue = value;
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
    return myValue.toString();
  }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.memory.adapters.CaptureObject;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A wrapper class to compare two capture info objects from perfd.
 * It also has a supplier to create Studio-side {@link CaptureObject} corresponding to the capture info object.
 * @param <T>
 */
public class CaptureEntry<T extends CaptureObject> {
  @NotNull private final Object myKey;
  @NotNull private final Supplier<T> myCaptureObjectSupplier;

  public CaptureEntry(@NotNull Object key, @NotNull Supplier<T> captureObjectSupplier) {
    myKey = key;
    myCaptureObjectSupplier = captureObjectSupplier;
  }

  @NotNull
  public T getCaptureObject() {
    return myCaptureObjectSupplier.get();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CaptureEntry)) {
      return false;
    }

    CaptureEntry other = (CaptureEntry)obj;
    return Objects.equals(myKey, other.myKey);
  }

  @Override
  public int hashCode() {
    return myKey.hashCode();
  }
}

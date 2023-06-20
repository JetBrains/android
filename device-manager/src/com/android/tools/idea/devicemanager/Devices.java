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

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;

public final class Devices {
  private Devices() {
  }

  public static int indexOf(@NotNull List<? extends Device> devices, @NotNull Key key) {
    OptionalInt optionalIndex = IntStream.range(0, devices.size())
      .filter(index -> devices.get(index).getKey().equals(key))
      .findFirst();

    return optionalIndex.orElse(-1);
  }
}

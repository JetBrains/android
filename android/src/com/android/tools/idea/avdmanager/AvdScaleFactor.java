/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Enum of common scale factors used for launching the emulator.
 * The value string is passed to the --scale parameter of the emulator.
 */
public enum AvdScaleFactor {
  TEN_TO_ONE(10, 1, "0.1"),
  FOUR_TO_ONE(4, 1, "0.25"),
  TWO_TO_ONE(2, 1, "0.5"),
  ONE_TO_ONE(1, 1, "1"),
  ONE_TO_TWO(1, 2, "2"),
  ONE_TO_THREE(1, 3, "3"),
  AUTO("Automatic", "auto");

  @NotNull private final String myHumanReadableName;
  @NotNull private final String myValue;

  @Nullable
  public static AvdScaleFactor findByValue(@NotNull String value) {
    for (AvdScaleFactor factor : AvdScaleFactor.values()) {
      if (value.equals(factor.getValue())) {
        return factor;
      }
    }
    return null;
  }

  AvdScaleFactor(int deviceDp, int screenPixels, @NotNull String value) {
    myHumanReadableName = String.format(Locale.getDefault(), "%ddp on device = %dpx on screen", deviceDp, screenPixels);
    myValue = value;
  }

  AvdScaleFactor(@NotNull String humanReadableName, @NotNull String value) {
    myHumanReadableName = humanReadableName;
    myValue = value;
  }

  @NotNull
  public String getValue() {
    return myValue;
  }

  @Override
  public String toString() {
    return myHumanReadableName;
  }
}

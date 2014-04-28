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
package com.android.tools.idea.structure;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Boolean-like enum that also permits a "not present" state.
 */
public enum ThreeStateBoolean {
  EMPTY("-", null),
  TRUE("true", Boolean.TRUE),
  FALSE("false", Boolean.FALSE);

  private final String myName;
  private final Boolean myValue;

  ThreeStateBoolean(@NotNull String name, @Nullable Boolean value) {
    myName = name;
    myValue = value;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Nullable
  public Boolean getValue() {
    return myValue;
  }

  @NotNull
  public static ThreeStateBoolean forValue(@Nullable Boolean b) {
    if (b == null) {
      return EMPTY;
    } else if (b) {
      return TRUE;
    } else {
      return FALSE;
    }
  }
}

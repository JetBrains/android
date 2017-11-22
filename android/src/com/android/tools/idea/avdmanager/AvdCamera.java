/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * A list of supported Android image camera types.
 */
public enum AvdCamera {
  NONE("None"),
  VIRTUAL_SCENE("VirtualScene"),
  EMULATED("Emulated"),
  WEBCAM("Webcam0");

  @NotNull private final String myName;

  AvdCamera(@NotNull String name) {
    myName = name;
  }

  public static AvdCamera fromName(@Nullable String name) {
    for (AvdCamera type : AvdCamera.values()) {
      if (type.myName.equalsIgnoreCase(name)) {
        return type;
      }
    }
    return NONE;
  }

  /**
   * The value needs to be converted before sent off to the emulator as a valid parameter
   */
  @NotNull
  public String getAsParameter() {
    return myName.toLowerCase(Locale.US);
  }

  @Override
  public String toString() {
    return myName;
  }
}

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
package com.android.tools.configurations;

import com.android.annotations.NonNull;

public enum AdaptiveIconShape {
  SQUARE("Square", "M50,0L100,0 100,100 0,100 0,0z"),
  CIRCLE("Circle", "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0Z"),
  ROUNDED_CORNERS("Rounded Corners",
                  "M50,0L92,0C96.42,0 100,4.58 100 8L100,92C100, 96.42 96.42 100 92 100L8 100C4.58, 100 0 96.42 0 92L0 8 C 0 4.42 4.42 0 8 0L50 0Z"),
  SQUIRCLE("Squircle", "M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z");

  private final String myName;
  private final String myPathDescription;

  AdaptiveIconShape(@NonNull String name, @NonNull String pathDescription) {
    myName = name;
    myPathDescription = pathDescription;
  }

  @NonNull
  public String getName() {
    return myName;
  }

  @NonNull
  public String getPathDescription() {
    return myPathDescription;
  }

  @NonNull
  public static AdaptiveIconShape getDefaultShape() {
    return SQUARE;
  }
}

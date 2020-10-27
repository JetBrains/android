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
package com.android.tools.idea.rendering;

/**
 * Nine aligned positions inside a rectangle.
 */
public enum Gravity {
  CENTER(0, 0),
  EAST(1, 0),
  NORTH_EAST(1, 1),
  NORTH(0, 1),
  NORTH_WEST(-1, 1),
  WEST(-1, 0),
  SOUTH_WEST(-1, -1),
  SOUTH(0, -1),
  SOUTH_EAST(1, -1);

  private final int myHorizontalAlignment;
  private final int myVerticalAlignment;

  Gravity(int horizontalAlignment, int verticalAlignment) {
    myHorizontalAlignment = horizontalAlignment;
    myVerticalAlignment = verticalAlignment;
  }

  /** Returns -1, 0 and 1 for WEST, CENTER and EAST respectively. */
  int getHorizontalAlignment() {
    return myHorizontalAlignment;
  }

  /** Returns -1, 0 and 1 for SOUTH, CENTER and NORTH respectively. */
  int getVerticalAlignment() {
    return myVerticalAlignment;
  }
}

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
package com.android.tools.idea.uibuilder.palette;

import org.jetbrains.annotations.NotNull;

public enum PaletteMode {
  ICON_AND_NAME("Icon and Name", 0),
  LARGE_ICONS("Large Icons", 4),
  SMALL_ICONS("Small Icons", 3);

  PaletteMode(@NotNull String title, int border) {
    myMenuTitle = title;
    myBorder = border;
  }

  private final String myMenuTitle;
  private final int myBorder;

  @NotNull
  public String getMenuTitle() {
    return myMenuTitle;
  }

  public int getBorder() {
    return myBorder;
  }
}

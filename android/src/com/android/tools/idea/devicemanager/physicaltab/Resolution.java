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
package com.android.tools.idea.devicemanager.physicaltab;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Resolution {
  private final int myWidth;
  private final int myHeight;

  public Resolution(int width, int height) {
    myWidth = width;
    myHeight = height;
  }

  int getWidth() {
    return myWidth;
  }

  int getHeight() {
    return myHeight;
  }

  @Override
  public int hashCode() {
    return 31 * myWidth + myHeight;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof Resolution)) {
      return false;
    }

    Resolution resolution = (Resolution)object;
    return myWidth == resolution.myWidth && myHeight == resolution.myHeight;
  }

  @Override
  public @NotNull String toString() {
    return myWidth + " × " + myHeight;
  }
}

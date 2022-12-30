/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.debug;

/**
 * This class is a partial copy of android.view.View.MeasureSpec
 */
public final class MeasureSpec {
  private static final int MODE_SHIFT = 30;
  private static final int MODE_MASK = 0x3 << MODE_SHIFT;

  public static final int UNSPECIFIED = 0 << MODE_SHIFT;

  public static final int EXACTLY = 1 << MODE_SHIFT;

  public static final int AT_MOST = 2 << MODE_SHIFT;

  public static int getMode(int measureSpec) {
    return (measureSpec & MODE_MASK);
  }

  public static int getSize(int measureSpec) {
    return (measureSpec & ~MODE_MASK);
  }

  public static String toString(int measureSpec) {
    int mode = getMode(measureSpec);
    int size = getSize(measureSpec);

    StringBuilder sb = new StringBuilder();

    if (mode == UNSPECIFIED) {
      sb.append("UNSPECIFIED ");
    }
    else if (mode == EXACTLY) {
      sb.append("EXACTLY ");
    }
    else if (mode == AT_MOST) {
      sb.append("AT_MOST ");
    }
    else {
      sb.append(mode).append(" ");
    }

    sb.append(size);
    return sb.toString();
  }
}
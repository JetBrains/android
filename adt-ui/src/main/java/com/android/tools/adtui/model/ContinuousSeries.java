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

package com.android.tools.adtui.model;

import com.android.annotations.NonNull;
import gnu.trove.TLongArrayList;

public class ContinuousSeries {

  @NonNull
  private final TLongArrayList mX = new TLongArrayList();

  @NonNull
  private final TLongArrayList mY = new TLongArrayList();

  private long mMaxY;

  public void add(long x, long y) {
    mMaxY = Math.max(mMaxY, y);
    mX.add(x);
    mY.add(y);
  }

  public int size() {
    return mX.size();
  }

  public long getX(int index) {
    return mX.get(index);
  }

  public long getY(int index) {
    return mY.get(index);
  }

  public long getMaxY() {
    return mMaxY;
  }
}

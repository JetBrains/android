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

import com.android.tools.adtui.Range;
import com.intellij.util.containers.ImmutableList;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;


public class LongDataSeries extends BaseDataSeries<Long> {

  @NotNull
  private final TLongArrayList mY = new TLongArrayList();

  @Override
  public void add(long x, Long y) {
    mX.add(x);
    mY.add(y);
  }

  @Override
  public Long getY(int index) {
    return mY.get(index);
  }
}

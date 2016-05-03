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
import com.android.tools.adtui.Range;

/**
 * Represents a view into a discrete series, where the data in view is only
 * within given x range.
 */
public class RangedDiscreteSeries {

  @NonNull
  private final Range mXRange;

  @NonNull
  private final DiscreteSeries mSeries;

  public RangedDiscreteSeries(@NonNull Class<? extends Enum> clazz, @NonNull Range xRange) {
    mXRange = xRange;
    mSeries = new DiscreteSeries(clazz);
  }

  @NonNull
  public DiscreteSeries getSeries() {
    return mSeries;
  }

  @NonNull
  public Range getXRange() {
    return mXRange;
  }
}

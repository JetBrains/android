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

/**
 * This class holds event information that is to be used by the event monitor components for
 * rendering.
 *
 * @param <T0> The first parameter is the value argument, often used as a hint to the component as
 *             to what the event was.
 * @param <T1> The second parameter is the value data argument, this is used as additional render
 *             information by the events components.
 */

public class EventAction<T0, T1> {

  @NonNull
  private final T0 mValue;
  @NonNull
  private final T1 mValueData;
  @NonNull
  private final long mStart;
  @NonNull
  private final long mEnd;

  @NonNull
  public T0 getValue() {
    return mValue;
  }

  @NonNull
  public T1 getValueData() {
    return mValueData;
  }

  @NonNull
  public long getStart() {
    return mStart;
  }

  @NonNull
  public long getEnd() {
    return mEnd;
  }

  public EventAction(@NonNull long start, @NonNull long end, @NonNull T0 value,
                     @NonNull T1 valueData) {
    this.mValue = value;
    this.mStart = start;
    this.mEnd = end;
    this.mValueData = valueData;
  }
}

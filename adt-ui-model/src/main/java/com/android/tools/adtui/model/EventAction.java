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

import org.jetbrains.annotations.NotNull;

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

  /**
   * Enum that defines the action of an event, used when the event has a Down, and Up action such
   * as MouseClick
   */
  public enum Action {
    NONE,
    DOWN,
    UP
  }

  /**
   * Enum that defines an activity state. Each activity started action, should have an associated
   * activity completed action.
   */
  public enum ActivityAction {
    NONE,
    ACTIVITY_STARTED,
    ACTIVITY_COMPLETED,
  }

  @NotNull
  private final T0 mValue;
  @NotNull
  private final T1 mValueData;
  @NotNull
  private final long mStartUs;
  @NotNull
  private final long mEndUs;

  @NotNull
  public T0 getValue() {
    return mValue;
  }

  @NotNull
  public T1 getValueData() {
    return mValueData;
  }

  @NotNull
  public long getStartUs() {
    return mStartUs;
  }

  @NotNull
  public long getEndUs() {
    return mEndUs;
  }

  public EventAction(@NotNull long start, @NotNull long end, @NotNull T0 value,
                     @NotNull T1 valueData) {
    this.mValue = value;
    this.mStartUs = start;
    this.mEndUs = end;
    this.mValueData = valueData;
  }
}

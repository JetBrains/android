/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.model.event;

import org.jetbrains.annotations.NotNull;

/**
 * This class holds event information that is to be used by the event monitor components for
 * rendering.
 *
 * @param <E> The parameter is the value argument, often used as a hint to the component as
 *             to what the event type was.
 */

public class EventAction<E> {

  private final long mStartUs;

  private final long mEndUs;

  @NotNull
  private final E mType;

  public long getStartUs() {
    return mStartUs;
  }

  public long getEndUs() {
    return mEndUs;
  }

  @NotNull
  public E getType() {
    return mType;
  }

  public EventAction(long start, long end, @NotNull E type) {
    this.mStartUs = start;
    this.mEndUs = end;
    this.mType = type;
  }
}

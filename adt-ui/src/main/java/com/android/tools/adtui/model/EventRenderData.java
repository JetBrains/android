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

/**
 * Class that defines event render information. The index is the order the element is to be drawn
 * The timestamp is a unique identifier.
 */
public class EventRenderData {

  private final int mIndex;
  private final long mTimestamp;

  public int getIndex() {
    return mIndex;
  }

  public long getTimestamp() {
    return mTimestamp;
  }

  public EventRenderData(int index, long timestamp) {
    mIndex = index;
    mTimestamp = timestamp;
  }
}
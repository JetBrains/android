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

public class ActivityAction extends EventAction<StackedEventType> {
  // Title to be displayed in the UI.
  private String myData;
  // If this ui object has an activity parent this hash should match the activity hash.
  private long myActivityContextHash;
  // Hash of activity/fragment that uniquely identifies that object.
  private long myHash;

  public ActivityAction(@NotNull long start, @NotNull long end, StackedEventType action, String data) {
    this(start, end, action, data, 0, 0);
  }

  public ActivityAction(@NotNull long start, @NotNull long end, StackedEventType action, String data, long hash, long activityContextHash) {
    super(start, end, action);
    myData = data;
    myHash = hash;
    myActivityContextHash = activityContextHash;
  }

  public String getData() {
    return myData;
  }

  public long getActivityContextHash() {
    return myActivityContextHash;
  }

  public long getHash() {
    return myHash;
  }

  @Override
  public int hashCode() {
    // Setting custom hashcode so we can compare two ActivityAction objects without having them be the exact same object.
    return  myData.hashCode() ^ Long.hashCode(getStartUs()) ^ Long.hashCode(getEndUs()) ^ getType().hashCode() ^ Long.hashCode(getHash());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ActivityAction)) {
      return false;
    }
    ActivityAction other = (ActivityAction)obj;
    return myData.equals(other.myData) &&
           getStartUs() == other.getStartUs() &&
           getEndUs() == other.getEndUs() &&
           getType() == other.getType() &&
           getHash() == other.getHash();
  }
}

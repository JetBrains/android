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

public class LifecycleAction extends EventAction<LifecycleEvent> {
  // Text to be displayed in the UI.
  private String myName;
  // Hash of activity/fragment that uniquely identifies that object.
  private long myHash;

  public LifecycleAction(long start, long end, LifecycleEvent action, String name) {
    this(start, end, action, name, 0);
  }

  public LifecycleAction(long start, long end, LifecycleEvent action, String name, long hash) {
    super(start, end, action);
    myName = name;
    myHash = hash;
  }

  public String getName() {
    return myName;
  }

  public long getHash() {
    return myHash;
  }

  @Override
  public int hashCode() {
    // Setting custom hashcode so we can compare two LifecycleAction objects without having them be the exact same object.
    return  myName.hashCode() ^ Long.hashCode(getStartUs()) ^ Long.hashCode(getEndUs()) ^ getType().hashCode() ^ Long.hashCode(getHash());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof LifecycleAction)) {
      return false;
    }
    LifecycleAction other = (LifecycleAction)obj;
    return myName.equals(other.myName) &&
           getStartUs() == other.getStartUs() &&
           getEndUs() == other.getEndUs() &&
           getType() == other.getType() &&
           getHash() == other.getHash();
  }
}

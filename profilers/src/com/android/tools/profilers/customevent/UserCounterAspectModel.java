/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.customevent;

import com.android.tools.adtui.model.AspectModel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * AspectModel that keeps track of when a new event has been found so that its event track can be dynamically loaded on screen.
 */
public class UserCounterAspectModel extends AspectModel<UserCounterAspectModel.Aspect> {

  public enum Aspect {
    USER_COUNTER
  }

  // Want to retain the order that events are added
  @NotNull private final Set<String> eventNames = new HashSet<>();

  /**
   * Adds the given string to eventNames if the name has not been seen before. Returns true if a name has been added.
   */
  public boolean add(String eventName) {
    if (eventNames.add(eventName)) {
      changed(Aspect.USER_COUNTER);
      return true;
    }
    return false;
  }

  public int getSize() {
    return eventNames.size();
  }

  @NotNull
  public Iterable<String> getEventNames() {
    return eventNames;
  }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.google.common.annotations.VisibleForTesting;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

final class KeyToConnectionTimeMap {
  @NotNull
  private final Map<Key, Instant> myMap;

  @NotNull
  private final Clock myClock;

  KeyToConnectionTimeMap() {
    this(Clock.systemDefaultZone());
  }

  @VisibleForTesting
  KeyToConnectionTimeMap(@NotNull Clock clock) {
    myMap = new ConcurrentHashMap<>();
    myClock = clock;
  }

  @NotNull
  Instant get(@NotNull Key key) {
    return myMap.computeIfAbsent(key, k -> myClock.instant());
  }

  void retainAll(@NotNull Collection<Key> keys) {
    myMap.keySet().retainAll(keys);
  }
}

/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.stats;

import com.android.annotations.NonNull;
import gnu.trove.TObjectLongHashMap;

/**
 * Helper to use StudioBuildStatsPersistenceComponent to capture build times.
 */
public class StudioBuildTime {

  public static final String KEY_SYNC_TIME = "sync-time";

  private static final TObjectLongHashMap<String> myTimestampMap = new TObjectLongHashMap<String>();

  /**
   * Registers a build start event for the given stat key.
   * Timers are not nested and this replaces the last start value for the given key.
   *
   * @param key A key representing the action being timed.
   */
  public static void start(@NonNull String key) {
    synchronized (myTimestampMap) {
      myTimestampMap.put(key, System.currentTimeMillis());
    }
  }

  /**
   * Registers a build stop event for the given stat key.
   * This generates a BuildRecord that will be sent in the next stats upload.
   * Does nothing if there hasn't been any corresponding start event.
   *
   * @param key A key representing the action being timed.
   */
  public static void stop(@NonNull String key) {
    try {
      long now = System.currentTimeMillis();
      long start = 0;
      synchronized (myTimestampMap) {
        start = myTimestampMap.remove(key);
      }
      if (start > 0 && start < now) {
        StudioBuildStatsPersistenceComponent i = StudioBuildStatsPersistenceComponent.getInstance();
        if (i != null) {
          i.addBuildRecord(new BuildRecord(key, Long.toString(now - start)));
        }
      }
    } catch (Throwable ignore) {}
  }

}

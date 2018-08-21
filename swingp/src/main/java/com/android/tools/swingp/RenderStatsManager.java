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
package com.android.tools.swingp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This class provides the global and per-thread storage of all call tree bases, which is represented by {@link ThreadStat}.
 */
public final class RenderStatsManager {
  private static final Set<ThreadStat> ourGlobalThreadStats = Collections.synchronizedSet(new HashSet<>());
  private static final ThreadLocal<ThreadStat> ourThreadStat = new ThreadLocal<ThreadStat>() {
    @Override
    protected ThreadStat initialValue() {
      ThreadStat threadStat = new ThreadStat();
      ourGlobalThreadStats.add(threadStat.setIsRecording(ourIsEnabled));
      return threadStat;
    }
  };

  private static volatile boolean ourIsEnabled = false;

  /**
   * Enables/disables swingp's collection of stats.
   * This method is safe to call at any time, but note that recording may not stop immediately,
   * as the system will allow incomplete stacks finish their recording.
   */
  public static void setIsEnabled(boolean isEnabled) {
    ourIsEnabled = isEnabled;
    ourGlobalThreadStats.forEach(threadStat -> threadStat.setIsRecording(ourIsEnabled));
    JComponentTreeManager.setEnabled(isEnabled);
  }

  @NotNull
  public static JsonElement getJson() {
    if (ourGlobalThreadStats.isEmpty()) {
      return JsonNull.INSTANCE;
    }

    JsonArray threads = new JsonArray();
    Set<ThreadStat> staleThreads = new HashSet<>();
    // Using an entrySet followed by a forEach results in only two locks, instead of N locks (N being the number of elements).
    ourGlobalThreadStats.forEach(threadStat -> {
      Thread thread = threadStat.getThread();
      if (thread == null || !thread.isAlive()) {
        staleThreads.add(threadStat); // Clean up once the thread is dead or has been GC'ed.
      }
      JsonElement threadElement = threadStat.getDescription();
      if (threadElement != JsonNull.INSTANCE) {
        threads.add(threadElement);
      }
    });
    ourGlobalThreadStats.removeAll(staleThreads); // Can't remove in the forEach, or it will cause a ConcurrentModificationException.
    return threads.size() == 0 ? JsonNull.INSTANCE : threads;
  }

  static void push(@NotNull MethodStat methodStat) {
    ourThreadStat.get().pushMethod(methodStat);
  }

  static void pop(@NotNull MethodStat verification) {
    ourThreadStat.get().popMethod(verification);
  }
}

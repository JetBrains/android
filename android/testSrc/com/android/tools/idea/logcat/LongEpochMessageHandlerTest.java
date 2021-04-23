/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat;

import static java.lang.Thread.yield;

import java.time.ZoneId;
import org.junit.Test;

public final class LongEpochMessageHandlerTest {
  String message = "2018-02-12 17:04:44.005   1282-12/app D/tag: Log message";

  @Test
  public void tryParse_showAsSecondsSinceEpochChangedInDifferentThread_doesNotCrash() throws InterruptedException {
    AndroidLogcatPreferences preferences = new AndroidLogcatPreferences();
    preferences.SHOW_AS_SECONDS_SINCE_EPOCH = false;
    LongEpochMessageHandler longEpochMessageHandler = new LongEpochMessageHandler(preferences, ZoneId.of("America/Los_Angeles"));

    // Toggle preferences.SHOW_AS_SECONDS_SINCE_EPOCH in a background thread.
    new Thread(() -> {
      for (int i1 = 0; i1 < 1000; i1++) {
        preferences.SHOW_AS_SECONDS_SINCE_EPOCH = !preferences.SHOW_AS_SECONDS_SINCE_EPOCH;
        Thread.yield();
      }
    }).start();

    // Call tryParse() in the main thread to ensure it doesn't crash.
    for (int i = 0; i < 1000; i++) {
      Thread.yield();
      longEpochMessageHandler.tryParse(this.message);
    }
  }
}
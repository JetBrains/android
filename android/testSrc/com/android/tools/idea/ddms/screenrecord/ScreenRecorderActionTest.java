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

package com.android.tools.idea.ddms.screenrecord;

import com.android.ddmlib.ScreenRecorderOptions;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

public class ScreenRecorderActionTest extends TestCase {
  public void testEmulatorScreenRecorderOptions() {
    ScreenRecorderOptions options =
            new ScreenRecorderOptions.Builder()
                    .setBitRate(6)
                    .setSize(600,400)
                    .build();
    assertEquals("--size 600x400 --bit-rate 6000000 /sdcard/1.mp4",
            ScreenRecorderAction.getEmulatorScreenRecorderOptions("/sdcard/1.mp4", options));

    options = new ScreenRecorderOptions.Builder().setTimeLimit(100, TimeUnit.SECONDS).build();
    assertEquals("--time-limit 100 /sdcard/1.mp4",
            ScreenRecorderAction.getEmulatorScreenRecorderOptions("/sdcard/1.mp4", options));

    options = new ScreenRecorderOptions.Builder().setTimeLimit(4, TimeUnit.MINUTES).build();
    assertEquals("--time-limit 180 /sdcard/1.mp4",
            ScreenRecorderAction.getEmulatorScreenRecorderOptions("/sdcard/1.mp4", options));
  }
}


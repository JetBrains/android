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
package com.android.tools.profilers;

import com.android.tools.adtui.model.Range;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ProfilerTimelineTest {

  @Test
  public void streaming() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline();
    Range dataRange = timeline.getDataRange();
    Range viewRange = timeline.getViewRange();
    dataRange.set(0, TimeUnit.SECONDS.toMicros(60));
    viewRange.set(0, 10);

    assertFalse(timeline.isStreaming());
    assertTrue(timeline.canStream());

    // Make sure streaming cannot be enabled if canStream is false.
    timeline.setCanStream(false);
    timeline.setStreaming(true);
    assertFalse(timeline.canStream());
    assertFalse(timeline.isStreaming());
    assertEquals(10, viewRange.getMax(), 0);
    assertEquals(10, viewRange.getLength(), 0);

    // Turn canStream + streaming on
    timeline.setCanStream(true);
    timeline.setStreaming(true);
    assertTrue(timeline.canStream());
    assertTrue(timeline.isStreaming());
    assertEquals(dataRange.getMax(), viewRange.getMax(), 0);
    assertEquals(10, viewRange.getLength(), 0);

    // Turn canStream off
    timeline.setCanStream(false);
    assertFalse(timeline.canStream());
    assertFalse(timeline.isStreaming());
  }
}
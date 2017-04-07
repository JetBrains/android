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
  public void constructor() throws Exception {
    Range dataRange = new Range(0, TimeUnit.SECONDS.toMicros(60));
    ProfilerTimeline timeline = new ProfilerTimeline(dataRange);

    Range viewRange = timeline.getViewRange();
    long buffer = timeline.getViewBuffer();

    assertEquals(dataRange.getLength(), viewRange.getLength(), 0);
    assertEquals(dataRange.getMin() - buffer, viewRange.getMin(), 0);
    assertEquals(dataRange.getMax() - buffer, viewRange.getMax(), 0);
    assertTrue(timeline.getSelectionRange().isEmpty());
  }

  @Test
  public void streaming() throws Exception {
    Range dataRange = new Range(0, TimeUnit.SECONDS.toMicros(60));
    ProfilerTimeline timeline = new ProfilerTimeline(dataRange);
    long buffer = timeline.getViewBuffer();
    Range viewRange = timeline.getViewRange();
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
    assertEquals(dataRange.getMax() - buffer, viewRange.getMax(), 0);
    assertEquals(10, viewRange.getLength(), 0);

    // Turn canStream off
    timeline.setCanStream(false);
    assertFalse(timeline.canStream());
    assertFalse(timeline.isStreaming());
  }

  @Test
  public void clampToDataRange() throws Exception {
    long maxUs = TimeUnit.SECONDS.toMicros(60);
    Range dataRange = new Range(0, maxUs);
    ProfilerTimeline timeline = new ProfilerTimeline(dataRange);
    long buffer = timeline.getViewBuffer();
    Range viewRange = timeline.getViewRange();

    // Ensure that the min view range is respected as a valid min
    assertEquals(-buffer, timeline.clampToDataRange(-buffer - 1), 0);

    // Ensure that the max accounts for the buffer
    assertEquals(maxUs - buffer, timeline.clampToDataRange(maxUs), 0);

    // Ensure that if data's min is smaller than view range's min, data's min will be used instead.
    viewRange.setMin(maxUs - buffer);
    assertEquals(0, timeline.clampToDataRange(-1), 0);
    dataRange.setMin(buffer);
    assertEquals(buffer, timeline.clampToDataRange(-1), 0);

    // Ensure that the max view range is not respected as a valid max
    viewRange.setMax(maxUs + buffer * 2);
    assertEquals(maxUs - buffer, timeline.clampToDataRange(maxUs), 0);
  }
}
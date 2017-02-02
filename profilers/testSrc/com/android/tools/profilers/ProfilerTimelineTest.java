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
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ProfilerTimelineTest {

  public static final double DELTA = 0.001;

  private final RelativeTimeConverter myRelativeTimeConverter = new RelativeTimeConverter(0);

  @Test
  public void streaming() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline(myRelativeTimeConverter);
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

  @Test
  public void testZoomIn() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline(myRelativeTimeConverter);
    Range dataRange = timeline.getDataRange();
    Range viewRange = timeline.getViewRange();
    dataRange.set(0, 100);
    viewRange.set(10, 90);

    timeline.zoom(-10, .5);
    assertEquals(15, viewRange.getMin(), DELTA);
    assertEquals(85, viewRange.getMax(), DELTA);

    timeline.zoom(-10, .1);
    assertEquals(16, viewRange.getMin(), DELTA);
    assertEquals(76, viewRange.getMax(), DELTA);

    timeline.zoom(-10, 0);
    assertEquals(16, viewRange.getMin(), DELTA);
    assertEquals(66, viewRange.getMax(), DELTA);

    timeline.zoom(-10, 1);
    assertEquals(26, viewRange.getMin(), DELTA);
    assertEquals(66, viewRange.getMax(), DELTA);
  }

  @Test
  public void testZoomOut() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline(myRelativeTimeConverter);
    Range dataRange = timeline.getDataRange();
    Range viewRange = timeline.getViewRange();
    dataRange.set(0, 100);
    viewRange.set(70, 70);

    // Not blocked
    timeline.zoom(40, .5);
    assertEquals(50, viewRange.getMin(), DELTA);
    assertEquals(90, viewRange.getMax(), DELTA);

    // Blocked to the right, use all the remaining offset to the left
    timeline.zoom(40, .5);
    assertEquals(20, viewRange.getMin(), DELTA);
    assertEquals(100, viewRange.getMax(), DELTA);

    // Expands to cover all the data range, with more space on the left
    viewRange.set(50, 95);
    timeline.zoom(60, .9);
    assertEquals(0, viewRange.getMin(), DELTA);
    assertEquals(100, viewRange.getMax(), DELTA);

    // Expands to cover all the data range, with more space on the right
    viewRange.set(5, 55);
    timeline.zoom(60, .1);
    assertEquals(0, viewRange.getMin(), DELTA);
    assertEquals(100, viewRange.getMax(), DELTA);

    // Blocked to the left, use all the remaining offset to the right
    viewRange.set(5, 15);
    timeline.zoom(60, .1);
    assertEquals(0, viewRange.getMin(), DELTA);
    assertEquals(70, viewRange.getMax(), DELTA);
  }

  @Test
  public void testPan() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline(myRelativeTimeConverter);
    Range dataRange = timeline.getDataRange();
    Range viewRange = timeline.getViewRange();
    dataRange.set(0, 100);
    viewRange.set(20, 30);

    timeline.pan(10);
    assertEquals(30, viewRange.getMin(), DELTA);
    assertEquals(40, viewRange.getMax(), DELTA);

    timeline.pan(-40);
    assertEquals(0, viewRange.getMin(), DELTA);
    assertEquals(10, viewRange.getMax(), DELTA);

    timeline.pan(140);
    assertEquals(90, viewRange.getMin(), DELTA);
    assertEquals(100, viewRange.getMax(), DELTA);
    assertFalse(timeline.isStreaming());

    timeline.setStreaming(true);
    assertTrue(timeline.isStreaming());
    // Test moving to the left stops streaming
    timeline.pan(-10);
    assertFalse(timeline.isStreaming());

    timeline.setStreaming(true);
    assertTrue(timeline.isStreaming());
    // Tests moving to the right doesn't stop streaming
    timeline.pan(10);
    assertTrue(timeline.isStreaming());
    // Test moving past the end doesn't stop streaming either
    timeline.pan(10);
  }
}
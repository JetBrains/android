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

  public static final double DELTA = 0.001;

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
    // Give time to update
    timeline.update(TimeUnit.SECONDS.toNanos(10));
    assertEquals(dataRange.getMax(), viewRange.getMax(), 0);
    assertEquals(10, viewRange.getLength(), 0);

    // Turn canStream off
    timeline.setCanStream(false);
    assertFalse(timeline.canStream());
    assertFalse(timeline.isStreaming());
  }

  @Test
  public void testZoomIn() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline();
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
    ProfilerTimeline timeline = new ProfilerTimeline();
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
  public void testZoomOutWhenDataNotFullyCoverView() {
    ProfilerTimeline timeline = new ProfilerTimeline();
    Range dataRange = timeline.getDataRange();
    Range viewRange = timeline.getViewRange();
    dataRange.set(50, 100);
    viewRange.set(30, 100);

    timeline.zoom(20, 1);
    assertEquals(10, viewRange.getMin(), DELTA);
    assertEquals(100, viewRange.getMax(), DELTA);

    timeline.zoom(-20, 1);
    assertEquals(30, viewRange.getMin(), DELTA);
    assertEquals(100, viewRange.getMax(), DELTA);
  }

  @Test
  public void testPan() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline();
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

  @Test
  public void testPause() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline();
    Range dataRange = timeline.getDataRange();
    Range viewRange = timeline.getViewRange();
    dataRange.set(0, 100);
    viewRange.set(0, 30);

    timeline.setStreaming(true);
    timeline.update(TimeUnit.SECONDS.toNanos(10));
    assertEquals(dataRange.getMax(), viewRange.getMax(), 0);
    assertEquals(30, viewRange.getLength(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(10), dataRange.getMax(), 0);

    timeline.setIsPaused(true);
    timeline.update(TimeUnit.SECONDS.toNanos(10));
    assertEquals(dataRange.getMax(), viewRange.getMax(), 0);
    assertEquals(30, viewRange.getLength(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(10), dataRange.getMax(), 0);
    assertFalse(timeline.isStreaming());
    assertTrue(timeline.isPaused());

    timeline.setIsPaused(false);
    timeline.update(TimeUnit.SECONDS.toNanos(10));
    assertEquals(dataRange.getMax(), viewRange.getMax(), 0);
    assertEquals(30, viewRange.getLength(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(30), dataRange.getMax(), 0);
  }

  @Test
  public void testReset() throws Exception {
    ProfilerTimeline timeline = new ProfilerTimeline();
    assertFalse(timeline.isStreaming());
    Range dataRange = timeline.getDataRange();
    Range viewRange = timeline.getViewRange();
    long deviceStartTimeNs = 20;
    timeline.reset(deviceStartTimeNs, 0);

    // Timeline should be streaming after reset
    assertTrue(timeline.isStreaming());
    // Timeline should not be paused after reset
    assertFalse(timeline.isPaused());

    // Timeline data range should be [deviceStartTimeUs, deviceStartTimeUs] after reset
    assertEquals(TimeUnit.NANOSECONDS.toMicros(deviceStartTimeNs), timeline.getDataRange().getMin(), 0);
    assertEquals(TimeUnit.NANOSECONDS.toMicros(deviceStartTimeNs), timeline.getDataRange().getMax(), 0);

    // Timeline view range should be [deviceStartTimeUs - DEFAULT_VIEW_LENGTH_US, deviceStartTimeUs] after reset
    assertEquals(TimeUnit.NANOSECONDS.toMicros(deviceStartTimeNs) - ProfilerTimeline.DEFAULT_VIEW_LENGTH_US,
                 timeline.getViewRange().getMin(), 0);
    assertEquals(TimeUnit.NANOSECONDS.toMicros(deviceStartTimeNs), timeline.getViewRange().getMax(), 0);

    timeline.update(TimeUnit.SECONDS.toNanos(1));
    //Validate that our max is equal to our initial of DEFAULT_VIEW_LENGTH + 1 second update.
    assertEquals(TimeUnit.SECONDS.toMicros(1),
                 dataRange.getMax(),
                 0);

    timeline.reset(0, TimeUnit.SECONDS.toNanos(1));
    assertEquals(dataRange.getMax(), viewRange.getMax(), 0);
    assertEquals(ProfilerTimeline.DEFAULT_VIEW_LENGTH_US, viewRange.getLength(), 0);

    timeline.update(TimeUnit.SECONDS.toNanos(1));
    assertEquals(dataRange.getMax(), viewRange.getMax(), dataRange.getMax() * .05f);
    assertEquals(ProfilerTimeline.DEFAULT_VIEW_LENGTH_US, viewRange.getLength(), 0);
    //Validate that our max is equal to our initial of DEFAULT_VIEW_LENGTH + 1 second initial + 1 second update.
    assertEquals(TimeUnit.SECONDS.toMicros(2), dataRange.getMax(), 0);
  }

  @Test
  public void testIdentityTimeConversionConversion() {
    ProfilerTimeline timeline = new ProfilerTimeline();
    assertEquals(1, timeline.convertToRelativeTimeUs(1000));
  }

  @Test
  public void testTimeConversionWithOffset() {
    final long OFFSET = 5000;
    ProfilerTimeline timeline = new ProfilerTimeline();
    timeline.reset(OFFSET, 0);
    assertEquals(OFFSET, timeline.getDataStartTimeNs());
    assertEquals(0, timeline.convertToRelativeTimeUs(5000));
    assertEquals(6, timeline.convertToRelativeTimeUs(11000));
    assertEquals(-6, timeline.convertToRelativeTimeUs(-1000));
  }
}
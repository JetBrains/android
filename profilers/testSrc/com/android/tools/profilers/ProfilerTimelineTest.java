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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updater;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ProfilerTimelineTest {

  public static final double DELTA = 0.001;

  @Test
  public void streaming() {
    Updater updater = new Updater(new FakeTimer());
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
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
  public void testZoomIn() {
    Updater updater = new Updater(new FakeTimer());
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
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
  public void testZoomOut() {
    Updater updater = new Updater(new FakeTimer());
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
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
    Updater updater = new Updater(new FakeTimer());
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
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
  public void testPan() {
    Updater updater = new Updater(new FakeTimer());
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
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
  public void testPause() {
    Updater updater = new Updater(new FakeTimer());
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
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
  public void testReset() {
    long resetTimeNs = TimeUnit.SECONDS.toNanos(10);
    long updateTimeNs = TimeUnit.SECONDS.toNanos(15);
    FakeTimer timer = new FakeTimer();
    timer.setCurrentTimeNs(resetTimeNs);

    Updater updater = new Updater(timer);
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
    assertFalse(timeline.isStreaming());
    Range dataRange = timeline.getDataRange();
    long startTimeNs = TimeUnit.SECONDS.toNanos(20);
    long endTimeNs = TimeUnit.SECONDS.toNanos(40);
    timeline.reset(startTimeNs, endTimeNs);

    // Timeline should be streaming after reset
    assertTrue(timeline.isStreaming());
    // Timeline should not be paused after reset
    assertFalse(timeline.isPaused());

    // Timeline data range should be [startTimeNs, endTimeNs] after reset
    assertEquals(TimeUnit.NANOSECONDS.toMicros(startTimeNs), timeline.getDataRange().getMin(), 0);
    assertEquals(TimeUnit.NANOSECONDS.toMicros(endTimeNs), timeline.getDataRange().getMax(), 0);

    // Timeline view range should be [endTimeNs - DEFAULT_VIEW_LENGTH_US, endTimeNs] after reset
    assertEquals(TimeUnit.NANOSECONDS.toMicros(endTimeNs) - ProfilerTimeline.DEFAULT_VIEW_LENGTH_US,
                 timeline.getViewRange().getMin(), 0);
    assertEquals(TimeUnit.NANOSECONDS.toMicros(endTimeNs), timeline.getViewRange().getMax(), 0);

    timer.setCurrentTimeNs(updateTimeNs);
    timeline.update(TimeUnit.SECONDS.toNanos(20));
    // Validate that either those we pass in 20 seconds to update, the timeline is only update for the duration between the reset+update
    // invocations.
    assertEquals(TimeUnit.SECONDS.toMicros(20), dataRange.getMin(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(45), dataRange.getMax(), 0);

    // Validate that subsequent update works as normal
    timer.setCurrentTimeNs(updateTimeNs * 2);
    timeline.update(TimeUnit.SECONDS.toNanos(20));
    assertEquals(TimeUnit.SECONDS.toMicros(20), dataRange.getMin(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(65), dataRange.getMax(), 0);
  }

  @Test
  public void testIdentityTimeConversionConversion() {
    Updater updater = new Updater(new FakeTimer());
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
    assertEquals(1, timeline.convertToRelativeTimeUs(1000));
  }

  @Test
  public void testTimeConversionWithOffset() {
    final long OFFSET = 5000;
    Updater updater = new Updater(new FakeTimer());
    ProfilerTimeline timeline = new ProfilerTimeline(updater);
    timeline.reset(OFFSET, OFFSET);
    assertEquals(OFFSET, timeline.getDataStartTimeNs());
    assertEquals(0, timeline.convertToRelativeTimeUs(5000));
    assertEquals(6, timeline.convertToRelativeTimeUs(11000));
    assertEquals(-6, timeline.convertToRelativeTimeUs(-1000));
  }

  @Test
  public void jumpToTargetOnTheLeft() {
    FakeTimer timer = new FakeTimer();
    ProfilerTimeline timeline = new ProfilerTimeline(new Updater(timer));
    Range viewRange = timeline.getViewRange();
    // View range initially:      #####
    //         target range: #
    //     View range after: #####
    viewRange.set(50, 100);
    Range targetRange = new Range(0, 10);
    timeline.setStreaming(true);
    timeline.ensureRangeFitsViewRange(targetRange);
    timer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(timeline.isStreaming());

    // target range is smaller than view range, so view range keeps the same length
    assertEquals(0, viewRange.getMin(), DELTA);
    assertEquals(50, viewRange.getMax(), DELTA);

    // View range initially:           #####
    //         target range: ##########
    //     View range after: ##########
    viewRange.set(100, 150);
    targetRange.set(0, 100);
    timeline.setStreaming(true);
    timeline.ensureRangeFitsViewRange(targetRange);
    timer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(timeline.isStreaming());
    // target range is larger than view range, so view range zooms out and grows to fit the target
    assertEquals(0, viewRange.getMin(), DELTA);
    assertEquals(100, viewRange.getMax(), DELTA);

    // View range initially:           #####
    //         target range:         ####
    //     View range after:         #####
    viewRange.set(100, 150);
    targetRange.set(90, 120);
    timeline.setStreaming(true);
    timeline.ensureRangeFitsViewRange(targetRange);
    timer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(timeline.isStreaming());
    // target range is partially outside of the view range, bring it back just enough so it fits the view range
    assertEquals(90, viewRange.getMin(), DELTA);
    assertEquals(140, viewRange.getMax(), DELTA);
  }

  @Test
  public void jumpToTargetOnTheRight() {
    FakeTimer timer = new FakeTimer();
    ProfilerTimeline timeline = new ProfilerTimeline(new Updater(timer));
    Range viewRange = timeline.getViewRange();
    // View range initially: #####
    //         target range:      #
    //     View range after:  #####
    viewRange.set(50, 100);
    Range targetRange = new Range(100, 110);
    timeline.setStreaming(true);
    timeline.ensureRangeFitsViewRange(targetRange);
    timer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(timeline.isStreaming());
    // target range is smaller than view range, so view range keeps the same length
    assertEquals(60, viewRange.getMin(), DELTA);
    assertEquals(110, viewRange.getMax(), DELTA);

    // View range initially: #####
    //         target range:      ##########
    //     View range after:      ##########
    viewRange.set(100, 150);
    targetRange.set(150, 250);
    timeline.setStreaming(true);
    timeline.ensureRangeFitsViewRange(targetRange);
    timer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(timeline.isStreaming());
    // target range is larger than view range, so view range zooms out and grows to fit the target
    assertEquals(150, viewRange.getMin(), DELTA);
    assertEquals(250, viewRange.getMax(), DELTA);

    // View range initially: #####
    //         target range:    ####
    //     View range after:   #####
    viewRange.set(100, 150);
    targetRange.set(130, 170);
    timeline.setStreaming(true);
    timeline.ensureRangeFitsViewRange(targetRange);
    timer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(timeline.isStreaming());
    // target range is partially outside of the view range, bring it back just enough so it fits the view range
    assertEquals(120, viewRange.getMin(), DELTA);
    assertEquals(170, viewRange.getMax(), DELTA);
  }

  @Test
  public void jumpToTargetWithinViewRange() {
    FakeTimer timer = new FakeTimer();
    ProfilerTimeline timeline = new ProfilerTimeline(new Updater(timer));
    Range viewRange = timeline.getViewRange();
    // View range initially: #####
    //         target range:  ##
    //     View range after: #####
    viewRange.set(50, 100);
    Range targetRange = new Range(60, 80);
    timeline.setStreaming(true);
    timeline.ensureRangeFitsViewRange(targetRange);
    timer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(timeline.isStreaming());
    // target range is smaller than view range, so view range keeps the same length and there is no need to move
    assertEquals(50, viewRange.getMin(), DELTA);
    assertEquals(100, viewRange.getMax(), DELTA);

    // View range initially:      #####
    //         target range: ###############
    //     View range after: ###############
    viewRange.set(50, 100);
    targetRange.set(0, 150);
    timeline.setStreaming(true);
    timeline.ensureRangeFitsViewRange(targetRange);
    timer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(timeline.isStreaming());
    // target range is larger than view range (in fact contains the view range) so view range zooms out and grows to fit the target
    assertEquals(0, viewRange.getMin(), DELTA);
    assertEquals(150, viewRange.getMax(), DELTA);
  }
}
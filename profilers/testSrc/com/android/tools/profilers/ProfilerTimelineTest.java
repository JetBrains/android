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
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ProfilerTimelineTest {

  public static final double DELTA = 0.001;

  private Range myDataRange;
  private Range myViewRange;
  private ProfilerTimeline myTimeline;
  private FakeTimer myTimer;

  @Before
  public void setup() {
    myTimer = new FakeTimer();
    Updater updater = new Updater(myTimer);
    myTimeline = new ProfilerTimeline(updater);
    myDataRange = myTimeline.getDataRange();
    myViewRange = myTimeline.getViewRange();
  }

  @Test
  public void streaming() {
    myDataRange.set(0, TimeUnit.SECONDS.toMicros(60));
    myViewRange.set(0, 10);

    assertFalse(myTimeline.isStreaming());
    assertTrue(myTimeline.canStream());

    // Make sure streaming cannot be enabled if canStream is false.
    myTimeline.setCanStream(false);
    myTimeline.setStreaming(true);
    assertFalse(myTimeline.canStream());
    assertFalse(myTimeline.isStreaming());
    assertEquals(10, myViewRange.getMax(), 0);
    assertEquals(10, myViewRange.getLength(), 0);

    // Turn canStream + streaming on
    myTimeline.setCanStream(true);
    myTimeline.setStreaming(true);
    assertTrue(myTimeline.canStream());
    assertTrue(myTimeline.isStreaming());
    // Give time to update
    myTimeline.update(TimeUnit.SECONDS.toNanos(10));
    assertEquals(myDataRange.getMax(), myViewRange.getMax(), 0);
    assertEquals(10, myViewRange.getLength(), 0);

    // Turn canStream off
    myTimeline.setCanStream(false);
    assertFalse(myTimeline.canStream());
    assertFalse(myTimeline.isStreaming());
  }

  @Test
  public void testZoomIn() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(false);
    myTimeline.setIsPaused(true);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    myViewRange.set(10, 90);

    myTimeline.zoom(-10, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(15, myViewRange.getMin(), DELTA);
    assertEquals(85, myViewRange.getMax(), DELTA);

    myTimeline.zoom(-10, .1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(16, myViewRange.getMin(), DELTA);
    assertEquals(76, myViewRange.getMax(), DELTA);

    myTimeline.zoom(-10, 0);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(16, myViewRange.getMin(), DELTA);
    assertEquals(66, myViewRange.getMax(), DELTA);

    myTimeline.zoom(-10, 1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(26, myViewRange.getMin(), DELTA);
    assertEquals(66, myViewRange.getMax(), DELTA);
  }

  @Test
  public void testZoomingAdjustStreamingMode() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myViewRange.set(50, 100);
    //put timeline in streaming mode, zooming should take it out, if our view range is less than data range.
    assertTrue(myTimeline.isStreaming());
    myTimeline.zoom(-10, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(55, myViewRange.getMin(), DELTA);
    assertEquals(95, myViewRange.getMax(), DELTA);
    assertFalse(myTimeline.isStreaming());

    //our timeline should continue streaming if our view range is greater than our data range, and we were initially streaming.
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimer.tick(0);
    myViewRange.set(-100, 100);
    assertTrue(myTimeline.isStreaming());
    myTimeline.zoom(-10, .5);
    assertTrue(myTimeline.isStreaming());
    // Need to pause the timeline so the tick doesn't increase the internal device time.
    myTimeline.setIsPaused(true);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(-95, myViewRange.getMin(), DELTA);
    assertEquals(100, myViewRange.getMax(), DELTA);
  }

  @Test
  public void testZoomOut() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(false);
    myTimeline.setIsPaused(true);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    myViewRange.set(70, 70);

    // Not blocked
    myTimeline.zoom(40, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(50, myViewRange.getMin(), DELTA);
    assertEquals(90, myViewRange.getMax(), DELTA);

    // Blocked to the right, use all the remaining offset to the left
    myTimeline.zoom(40, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(20, myViewRange.getMin(), DELTA);
    assertEquals(100, myViewRange.getMax(), DELTA);

    // Expands to cover all the data range, with more space on the left
    myViewRange.set(50, 95);
    myTimeline.zoom(60, .9);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(0, myViewRange.getMin(), DELTA);
    assertEquals(100, myViewRange.getMax(), DELTA);

    // Expands to cover all the data range, with more space on the right
    myViewRange.set(5, 55);
    myTimeline.zoom(60, .1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(0, myViewRange.getMin(), DELTA);
    assertEquals(100, myViewRange.getMax(), DELTA);

    // Blocked to the left, use all the remaining offset to the right
    myViewRange.set(5, 15);
    myTimeline.zoom(60, .1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(0, myViewRange.getMin(), DELTA);
    assertEquals(70, myViewRange.getMax(), DELTA);

    // Test zoomOutBy
    myViewRange.set(0, 10);
    assertEquals(10, myViewRange.getLength(), DELTA);
    myTimeline.zoom(20);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Time enough to complete zooming out
    // We can't zoom out further than 0, so we zoom out towards the right side. The view range should be [0, 30]
    assertEquals(0, myViewRange.getMin(), DELTA);
    assertEquals(30, myViewRange.getMax(), DELTA);
    assertEquals(30, myViewRange.getLength(), DELTA);

    myViewRange.set(50, 60);
    assertEquals(10, myViewRange.getLength(), DELTA);
    myTimeline.zoom(20);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Time enough to complete zooming out
    // We should zoom out evenly on both sides, so the view range should be [40, 70]
    assertEquals(40, myViewRange.getMin(), DELTA);
    assertEquals(70, myViewRange.getMax(), DELTA);
    assertEquals(30, myViewRange.getLength(), DELTA);
  }

  @Test
  public void testZoomOutWhenDataNotFullyCoverView() {
    myTimeline.reset(TimeUnit.MICROSECONDS.toNanos(50), TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(false);
    myTimeline.setIsPaused(true);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    myViewRange.set(30, 100);

    myTimeline.zoom(20, 1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(10, myViewRange.getMin(), DELTA);
    assertEquals(100, myViewRange.getMax(), DELTA);

    myTimeline.zoom(-20, 1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(30, myViewRange.getMin(), DELTA);
    assertEquals(100, myViewRange.getMax(), DELTA);
  }

  @Test
  public void testPan() {
    myDataRange.set(0, 100);
    myViewRange.set(20, 30);

    myTimeline.pan(10);
    assertEquals(30, myViewRange.getMin(), DELTA);
    assertEquals(40, myViewRange.getMax(), DELTA);

    myTimeline.pan(-40);
    assertEquals(0, myViewRange.getMin(), DELTA);
    assertEquals(10, myViewRange.getMax(), DELTA);

    myTimeline.pan(140);
    assertEquals(90, myViewRange.getMin(), DELTA);
    assertEquals(100, myViewRange.getMax(), DELTA);
    assertFalse(myTimeline.isStreaming());

    myTimeline.setStreaming(true);
    assertTrue(myTimeline.isStreaming());
    // Test moving to the left stops streaming
    myTimeline.pan(-10);
    assertFalse(myTimeline.isStreaming());

    myTimeline.setStreaming(true);
    assertTrue(myTimeline.isStreaming());
    // Tests moving to the right doesn't stop streaming
    myTimeline.pan(10);
    assertTrue(myTimeline.isStreaming());
    // Test moving past the end doesn't stop streaming either
    myTimeline.pan(10);
  }

  @Test
  public void testPause() {
    myDataRange.set(0, 100);
    myViewRange.set(0, 30);

    myTimeline.setStreaming(true);
    myTimeline.update(TimeUnit.SECONDS.toNanos(10));
    assertEquals(myDataRange.getMax(), myViewRange.getMax(), 0);
    assertEquals(30, myViewRange.getLength(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(10), myDataRange.getMax(), 0);

    myTimeline.setIsPaused(true);
    myTimeline.update(TimeUnit.SECONDS.toNanos(10));
    assertEquals(myDataRange.getMax(), myViewRange.getMax(), 0);
    assertEquals(30, myViewRange.getLength(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(10), myDataRange.getMax(), 0);
    assertFalse(myTimeline.isStreaming());
    assertTrue(myTimeline.isPaused());

    myTimeline.setIsPaused(false);
    myTimeline.update(TimeUnit.SECONDS.toNanos(10));
    assertEquals(myDataRange.getMax(), myViewRange.getMax(), 0);
    assertEquals(30, myViewRange.getLength(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(30), myDataRange.getMax(), 0);
  }

  @Test
  public void testReset() {
    long resetTimeNs = TimeUnit.SECONDS.toNanos(10);
    long updateTimeNs = TimeUnit.SECONDS.toNanos(15);
    myTimer.setCurrentTimeNs(resetTimeNs);
    assertFalse(myTimeline.isStreaming());
    long startTimeNs = TimeUnit.SECONDS.toNanos(20);
    long endTimeNs = TimeUnit.SECONDS.toNanos(40);
    myTimeline.reset(startTimeNs, endTimeNs);

    // Timeline should be streaming after reset
    assertTrue(myTimeline.isStreaming());
    // Timeline should not be paused after reset
    assertFalse(myTimeline.isPaused());

    // Timeline data range should be [startTimeNs, endTimeNs] after reset
    assertEquals(TimeUnit.NANOSECONDS.toMicros(startTimeNs), myDataRange.getMin(), 0);
    assertEquals(TimeUnit.NANOSECONDS.toMicros(endTimeNs), myDataRange.getMax(), 0);

    // Timeline view range should be [endTimeNs - DEFAULT_VIEW_LENGTH_US, endTimeNs] after reset
    assertEquals(TimeUnit.NANOSECONDS.toMicros(endTimeNs) - ProfilerTimeline.DEFAULT_VIEW_LENGTH_US,
                 myViewRange.getMin(), 0);
    assertEquals(TimeUnit.NANOSECONDS.toMicros(endTimeNs), myViewRange.getMax(), 0);

    myTimer.setCurrentTimeNs(updateTimeNs);
    myTimeline.update(TimeUnit.SECONDS.toNanos(20));
    // Validate that either those we pass in 20 seconds to update, the timeline is only update for the duration between the reset+update
    // invocations.
    assertEquals(TimeUnit.SECONDS.toMicros(20), myDataRange.getMin(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(45), myDataRange.getMax(), 0);

    // Validate that subsequent update works as normal
    myTimer.setCurrentTimeNs(updateTimeNs * 2);
    myTimeline.update(TimeUnit.SECONDS.toNanos(20));
    assertEquals(TimeUnit.SECONDS.toMicros(20), myDataRange.getMin(), 0);
    assertEquals(TimeUnit.SECONDS.toMicros(65), myDataRange.getMax(), 0);
  }

  @Test
  public void testIdentityTimeConversionConversion() {
    assertEquals(1, myTimeline.convertToRelativeTimeUs(1000));
  }

  @Test
  public void testTimeConversionWithOffset() {
    final long OFFSET = 5000;
    myTimeline.reset(OFFSET, OFFSET);
    assertEquals(OFFSET, myTimeline.getDataStartTimeNs());
    assertEquals(0, myTimeline.convertToRelativeTimeUs(5000));
    assertEquals(6, myTimeline.convertToRelativeTimeUs(11000));
    assertEquals(-6, myTimeline.convertToRelativeTimeUs(-1000));
  }

  @Test
  public void frameRangeWithPercent() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(false);
    myTimeline.setIsPaused(true);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    myViewRange.set(0, 100);

    // Test view gets set to target range without padding.
    Range targetRange = new Range( 50, 70);
    myTimeline.frameViewToRange(targetRange, 0);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(targetRange.getMin(), myViewRange.getMin(), DELTA);
    assertEquals(targetRange.getMax(), myViewRange.getMax(), DELTA);

    // Test view gets set to target range with padding. Also outside current view.
    targetRange = new Range( 80, 90);
    myTimeline.frameViewToRange(targetRange, .1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(79, myViewRange.getMin(), DELTA);
    assertEquals(91, myViewRange.getMax(), DELTA);

    // Test view gets set to target range capped at max data.
    targetRange = new Range( 50, myDataRange.getMax() + 10);
    myTimeline.frameViewToRange(targetRange, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertEquals(20, myViewRange.getMin(), DELTA);
    assertEquals(myDataRange.getMax(), myViewRange.getMax(), DELTA);
  }

  @Test
  public void frameRangeDisablesStreaming() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(true);
    myTimeline.frameViewToRange(new Range( 50, 70), 0);
    assertFalse(myTimeline.isStreaming());
  }

  @Test
  public void jumpToTargetOnTheLeft() {
    // Give time to make data range non-empty, streaming will update the data range.
    myTimer.tick(TimeUnit.MICROSECONDS.toNanos(300));

    // View range initially:      #####
    //         target range: #
    //     View range after: #####
    myViewRange.set(50, 100);
    Range targetRange = new Range(0, 10);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(myTimeline.isStreaming());

    // target range is smaller than view range, so view range keeps the same length
    assertEquals(0, myViewRange.getMin(), DELTA);
    assertEquals(50, myViewRange.getMax(), DELTA);

    // View range initially:           #####
    //         target range: ##########
    //     View range after: ##########
    myViewRange.set(100, 150);
    targetRange.set(0, 100);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(myTimeline.isStreaming());
    // target range is larger than view range, so view range zooms out and grows to fit the target
    assertEquals(0, myViewRange.getMin(), DELTA);
    assertEquals(100, myViewRange.getMax(), DELTA);

    // View range initially:           #####
    //         target range:         ####
    //     View range after:         #####
    myViewRange.set(100, 150);
    targetRange.set(90, 120);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(myTimeline.isStreaming());
    // target range is partially outside of the view range, bring it back just enough so it fits the view range, make 105 as the middle.
    assertEquals(80, myViewRange.getMin(), DELTA);
    assertEquals(130, myViewRange.getMax(), DELTA);
  }

  @Test
  public void jumpToTargetOnTheRight() {
    // Give time to make data range non-empty, streaming will update the data range.
    myTimer.tick(TimeUnit.MICROSECONDS.toNanos(300));
    // View range initially: #####
    //         target range:      #
    //     View range after:  #####
    myViewRange.set(50, 100);
    Range targetRange = new Range(100, 110);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(myTimeline.isStreaming());
    // target range is smaller than view range, so view range keeps the same length
    assertEquals(80, myViewRange.getMin(), DELTA);
    assertEquals(130, myViewRange.getMax(), DELTA);

    // View range initially: #####
    //         target range:      ##########
    //     View range after:      ##########
    // Uses a dynamic range max as streaming will change the data range.
    double targetRangeMax = myDataRange.getMax();
    myViewRange.set(targetRangeMax - 200, targetRangeMax - 100);
    targetRange.set(targetRangeMax - 100, targetRangeMax);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(myTimeline.isStreaming());
    // target range is larger than view range, so view range zooms out and grows to fit the target
    assertEquals(targetRangeMax - 100, myViewRange.getMin(), DELTA);
    assertEquals(targetRangeMax, myViewRange.getMax(), DELTA);

    // View range initially: #####
    //         target range:    ####
    //     View range after:   #####
    myViewRange.set(100, 150);
    targetRange.set(130, 170);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(myTimeline.isStreaming());
    // target range is partially outside of the view range, bring it back just enough so it fits the view range, and make 125 the middle.
    assertEquals(125, myViewRange.getMin(), DELTA);
    assertEquals(175, myViewRange.getMax(), DELTA);
  }

  @Test
  public void jumpToTargetWithinViewRange() {
    // Give time to make data range non-empty, streaming will update the data range.
    myTimer.tick(TimeUnit.MICROSECONDS.toNanos(300));

    // View range initially: #####
    //         target range:  ##
    //     View range after: #####
    myViewRange.set(50, 100);
    Range targetRange = new Range(60, 80);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(myTimeline.isStreaming());
    // target range is smaller than view range, so view range keeps the same length and there is no need to move, and make 70 the middle.
    assertEquals(45, myViewRange.getMin(), DELTA);
    assertEquals(95, myViewRange.getMax(), DELTA);

    // View range initially:      #####
    //         target range: ###############
    //     View range after: ###############
    myViewRange.set(50, 100);
    targetRange.set(0, 150);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertFalse(myTimeline.isStreaming());
    // target range is larger than view range (in fact contains the view range) so view range zooms out and grows to fit the target
    assertEquals(0, myViewRange.getMin(), DELTA);
    assertEquals(150, myViewRange.getMax(), DELTA);
  }
}
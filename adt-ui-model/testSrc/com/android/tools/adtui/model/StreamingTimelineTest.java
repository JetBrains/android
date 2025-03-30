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
package com.android.tools.adtui.model;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.updater.Updater;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class StreamingTimelineTest {

  public static final double DELTA = 0.001;

  private Range myDataRange;
  private Range myViewRange;
  private StreamingTimeline myTimeline;
  private FakeTimer myTimer;

  @Before
  public void setup() {
    myTimer = new FakeTimer();
    Updater updater = new Updater(myTimer);
    myTimeline = new StreamingTimeline(updater);
    myDataRange = myTimeline.getDataRange();
    myViewRange = myTimeline.getViewRange();
  }

  @Test
  public void streaming() {
    myDataRange.set(0, TimeUnit.SECONDS.toMicros(60));
    myViewRange.set(0, 10);

    assertThat(myTimeline.isStreaming()).isFalse();
    assertThat(myTimeline.canStream()).isTrue();

    // Make sure streaming cannot be enabled if canStream is false.
    myTimeline.setCanStream(false);
    myTimeline.setStreaming(true);
    assertThat(myTimeline.canStream()).isFalse();
    assertThat(myTimeline.isStreaming()).isFalse();
    assertThat(myViewRange.getMax()).isWithin(0).of(10);
    assertThat(myViewRange.getLength()).isWithin(0).of(10);

    // Turn canStream + streaming on
    myTimeline.setCanStream(true);
    myTimeline.setStreaming(true);
    assertThat(myTimeline.canStream()).isTrue();
    assertThat(myTimeline.isStreaming()).isTrue();
    // Give time to update
    myTimeline.update(TimeUnit.SECONDS.toNanos(10));
    assertThat(myViewRange.getMax()).isWithin(0).of(myDataRange.getMax());
    assertThat(myViewRange.getLength()).isWithin(0).of(10);

    // Turn canStream off
    myTimeline.setCanStream(false);
    assertThat(myTimeline.canStream()).isFalse();
    assertThat(myTimeline.isStreaming()).isFalse();
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
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(15);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(85);

    myTimeline.zoom(-10, .1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(16);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(76);

    myTimeline.zoom(-10, 0);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(16);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(66);

    myTimeline.zoom(-10, 1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(26);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(66);
  }

  @Test
  public void zoomInMoreThanViewRangeShouldStillResultInValidViewRange() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(false);
    myTimeline.setIsPaused(true);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    myViewRange.set(0, 100);

    myTimeline.zoom(-120, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.isEmpty()).isFalse();
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(45);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(55);
  }

  @Test
  public void previousAdjustRangeCloseToMiddleViewShouldNotAffectTheCurrentViewRange() {
    myTimeline.reset(0, secToNanos(100));
    myTimeline.setStreaming(false);
    myTimeline.setIsPaused(true);

    myViewRange.set(secToUs(0), secToUs(10));
    myTimeline.adjustRangeCloseToMiddleView(new Range(secToUs(50), secToUs(60)));
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);

    myTimeline.reset(secToNanos(200), secToNanos(300));
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(secToUs(300));
  }

  @Test
  public void previousZoomingShouldNotAffectTheCurrentViewRange() {
    myTimeline.reset(0, secToNanos(100));
    myTimeline.setStreaming(false);
    myTimeline.setIsPaused(true);

    myViewRange.set(secToUs(0), secToUs(10));
    myTimeline.zoomIn();

    myTimer.setCurrentTimeNs(secToNanos(200));

    myTimeline.reset(secToNanos(200), secToNanos(300));
    myTimeline.setStreaming(false);
    // {@link ProfilerTimeline#update(long elapsedNs)} changes elapsed time to |currentTime - lastResetTime|,
    // so to make the elapsed time not zero (i.e 2 Seconds exactly) we need to set the current time.
    myTimer.setCurrentTimeNs(secToNanos(202));
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(secToUs(300));
  }

  @Test
  public void testZoomingAdjustStreamingMode() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myViewRange.set(50, 100);
    //put timeline in streaming mode, zooming should take it out, if our view range is less than data range.
    assertThat(myTimeline.isStreaming()).isTrue();
    myTimeline.zoom(-10, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(55);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(95);
    assertThat(myTimeline.isStreaming()).isFalse();

    //our timeline should continue streaming if our view range is greater than our data range, and we were initially streaming.
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimer.tick(0);
    myViewRange.set(-100, 100);
    assertThat(myTimeline.isStreaming()).isTrue();
    myTimeline.zoom(-10, .5);
    assertThat(myTimeline.isStreaming()).isTrue();
    // Need to pause the timeline so the tick doesn't increase the internal device time.
    myTimeline.setIsPaused(true);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(-95);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);
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
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(50);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(90);

    // Blocked to the right, use all the remaining offset to the left
    myTimeline.zoom(40, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(20);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);

    // Expands to cover all the data range, with more space on the left
    myViewRange.set(50, 95);
    myTimeline.zoom(60, .9);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);

    // Expands to cover all the data range, with more space on the right
    myViewRange.set(5, 55);
    myTimeline.zoom(60, .1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);

    // Blocked to the left, use all the remaining offset to the right
    myViewRange.set(5, 15);
    myTimeline.zoom(60, .1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(70);

    // Test zoomOutBy
    myViewRange.set(0, 10);
    assertThat(myViewRange.getLength()).isWithin(DELTA).of(10);
    myTimeline.zoom(20);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Time enough to complete zooming out
    // We can't zoom out further than 0, so we zoom out towards the right side. The view range should be [0, 30]
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(30);
    assertThat(myViewRange.getLength()).isWithin(DELTA).of(30);

    myViewRange.set(50, 60);
    assertThat(myViewRange.getLength()).isWithin(DELTA).of(10);
    myTimeline.zoom(20);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Time enough to complete zooming out
    // We should zoom out evenly on both sides, so the view range should be [40, 70]
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(40);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(70);
    assertThat(myViewRange.getLength()).isWithin(DELTA).of(30);
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
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(10);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);

    myTimeline.zoom(-20, 1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(30);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);
  }

  @Test
  public void testPan() {
    myDataRange.set(0, 100);
    myViewRange.set(20, 30);

    myTimeline.panView(10);
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(30);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(40);

    myTimeline.panView(-40);
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(10);

    myTimeline.panView(140);
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(90);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);
    assertThat(myTimeline.isStreaming()).isFalse();

    myTimeline.setStreaming(true);
    assertThat(myTimeline.isStreaming()).isTrue();
    // Test moving to the left stops streaming
    myTimeline.panView(-10);
    assertThat(myTimeline.isStreaming()).isFalse();

    myTimeline.setStreaming(true);
    assertThat(myTimeline.isStreaming()).isTrue();
    // Tests moving to the right doesn't stop streaming
    myTimeline.panView(10);
    assertThat(myTimeline.isStreaming()).isTrue();
    // Test moving past the end doesn't stop streaming either
    myTimeline.panView(10);
  }

  @Test
  public void testPause() {
    myDataRange.set(0, 100);
    myViewRange.set(0, 30);

    myTimeline.setStreaming(true);
    myTimeline.update(TimeUnit.SECONDS.toNanos(10));
    assertThat(myViewRange.getMax()).isWithin(0).of(myDataRange.getMax());
    assertThat(myViewRange.getLength()).isWithin(0).of(30);
    assertThat(myDataRange.getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(10));

    myTimeline.setIsPaused(true);
    myTimeline.update(TimeUnit.SECONDS.toNanos(10));
    assertThat(myViewRange.getMax()).isWithin(0).of(myDataRange.getMax());
    assertThat(myViewRange.getLength()).isWithin(0).of(30);
    assertThat(myDataRange.getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(10));
    assertThat(myTimeline.isStreaming()).isFalse();
    assertThat(myTimeline.isPaused()).isTrue();

    myTimeline.setIsPaused(false);
    myTimeline.update(TimeUnit.SECONDS.toNanos(10));
    assertThat(myViewRange.getMax()).isWithin(0).of(myDataRange.getMax());
    assertThat(myViewRange.getLength()).isWithin(0).of(30);
    assertThat(myDataRange.getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(30));
  }

  @Test
  public void testReset() {
    long resetTimeNs = TimeUnit.SECONDS.toNanos(10);
    long updateTimeNs = TimeUnit.SECONDS.toNanos(15);
    myTimer.setCurrentTimeNs(resetTimeNs);
    assertThat(myTimeline.isStreaming()).isFalse();
    long startTimeNs = TimeUnit.SECONDS.toNanos(20);
    long endTimeNs = TimeUnit.SECONDS.toNanos(40);
    myTimeline.reset(startTimeNs, endTimeNs);

    // Timeline should be streaming after reset
    assertThat(myTimeline.isStreaming()).isTrue();
    // Timeline should not be paused after reset
    assertThat(myTimeline.isPaused()).isFalse();

    // Timeline data range should be [startTimeNs, endTimeNs] after reset
    assertThat(myDataRange.getMin()).isWithin(0).of(TimeUnit.NANOSECONDS.toMicros(startTimeNs));
    assertThat(myDataRange.getMax()).isWithin(0).of(TimeUnit.NANOSECONDS.toMicros(endTimeNs));

    // Timeline view range should be [endTimeNs - DEFAULT_VIEW_LENGTH_US, endTimeNs] after reset
    assertThat(myViewRange.getMin()).isWithin(0).of(TimeUnit.NANOSECONDS.toMicros(endTimeNs) - StreamingTimeline.DEFAULT_VIEW_LENGTH_US);
    assertThat(myViewRange.getMax()).isWithin(0).of(TimeUnit.NANOSECONDS.toMicros(endTimeNs));

    myTimer.setCurrentTimeNs(updateTimeNs);
    myTimeline.update(TimeUnit.SECONDS.toNanos(20));
    // Validate that either those we pass in 20 seconds to update, the timeline is only update for the duration between the reset+update
    // invocations.
    assertThat(myDataRange.getMin()).isWithin(0).of(TimeUnit.SECONDS.toMicros(20));
    assertThat(myDataRange.getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(45));

    // Validate that subsequent update works as normal
    myTimer.setCurrentTimeNs(updateTimeNs * 2);
    myTimeline.update(TimeUnit.SECONDS.toNanos(20));
    assertThat(myDataRange.getMin()).isWithin(0).of(TimeUnit.SECONDS.toMicros(20));
    assertThat(myDataRange.getMax()).isWithin(0).of(TimeUnit.SECONDS.toMicros(65));
  }

  @Test
  public void testIdentityTimeConversionConversion() {
    assertThat(myTimeline.convertToRelativeTimeUs(1000)).isEqualTo(1);
  }

  @Test
  public void testTimeConversionWithOffset() {
    final long OFFSET = 5000;
    myTimeline.reset(OFFSET, OFFSET);
    assertThat(myTimeline.getDataStartTimeNs()).isEqualTo(OFFSET);
    assertThat(myTimeline.convertToRelativeTimeUs(5000)).isEqualTo(0);
    assertThat(myTimeline.convertToRelativeTimeUs(11000)).isEqualTo(6);
    assertThat(myTimeline.convertToRelativeTimeUs(-1000)).isEqualTo(-6);
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
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(targetRange.getMin());
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(targetRange.getMax());

    // Test view gets set to target range with padding. Also outside current view.
    targetRange = new Range( 80, 90);
    myTimeline.frameViewToRange(targetRange, .1);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(79);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(91);

    // Test view gets set to target range capped at max data.
    targetRange = new Range( 50, myDataRange.getMax() + 10);
    myTimeline.frameViewToRange(targetRange, .5);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(20);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(myDataRange.getMax());
  }

  @Test
  public void frameRangeDisablesStreaming() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(true);
    myTimeline.frameViewToRange(new Range( 50, 70), 0);
    assertThat(myTimeline.isStreaming()).isFalse();
  }

  @Test
  public void frameToViewPointRange() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(true);
    myViewRange.set(0, 20);

    Range pointRange = new Range(50, 50);
    myTimeline.frameViewToRange(pointRange, 0);
    myTimer.tick(TimeUnit.SECONDS.toNanos(5));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(40);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(60);
  }

  @Test
  public void frameToViewEmptyRange() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(true);
    myViewRange.set(0, 20);

    Range emptyRange = new Range();
    myTimeline.frameViewToRange(emptyRange, 0);
    myTimer.tick(TimeUnit.SECONDS.toNanos(5));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(20);
  }

  @Test
  public void adjustViewForPointRange() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(true);
    myViewRange.set(0, 20);

    Range pointRange = new Range(50, 50);
    myTimeline.adjustRangeCloseToMiddleView(pointRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(5));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(40);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(60);
  }

  @Test
  public void adjustViewForEmptyRange() {
    myTimeline.reset(0, TimeUnit.MICROSECONDS.toNanos(100));
    myTimeline.setStreaming(true);
    myViewRange.set(0, 20);

    Range emptyRange = new Range();
    myTimeline.adjustRangeCloseToMiddleView(emptyRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(5));
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(20);
  }

  @Test
  public void viewRangeChangedWhenJumpingToTargetOnTheLeftOfIt() {
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
    assertThat(myTimeline.isStreaming()).isFalse();

    // target range is smaller than view range, so view range keeps the same length
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(50);

    // View range initially:           #####
    //         target range: ##########
    //     View range after: ##########
    myViewRange.set(100, 150);
    targetRange.set(0, 100);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertThat(myTimeline.isStreaming()).isFalse();
    // target range is larger than view range, so view range zooms out and grows to fit the target
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);

    // View range initially:           #####
    //         target range:         ####
    //     View range after:         #####
    myViewRange.set(100, 150);
    targetRange.set(90, 120);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertThat(myTimeline.isStreaming()).isFalse();
    // target range is partially outside of the view range, bring it back just enough so it fits the view range, make 105 as the middle.
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(80);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(130);
  }

  @Test
  public void viewRangeChangedWhenJumpingToTargetOnTheRightOfIt() {
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
    assertThat(myTimeline.isStreaming()).isFalse();
    // target range is smaller than view range, so view range keeps the same length
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(80);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(130);

    // View range initially: ##########
    //         target range:           ##########
    //     View range after:           ##########
    // Uses a dynamic range max as streaming will change the data range.
    double targetRangeMax = myDataRange.getMax();
    myViewRange.set(targetRangeMax - 200, targetRangeMax - 100);
    targetRange.set(targetRangeMax - 100, targetRangeMax);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertThat(myTimeline.isStreaming()).isFalse();
    // target range is completely on the right of the view range and both have the same length, so view range gets shifted to the right
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(targetRangeMax - 100);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(targetRangeMax);

    // View range initially: #####
    //         target range:    ####
    //     View range after:   #####
    myViewRange.set(100, 150);
    targetRange.set(130, 170);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertThat(myTimeline.isStreaming()).isFalse();
    // target range is partially outside of the view range, bring it back just enough so it fits the view range, and make 125 the middle.
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(125);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(175);
  }

  @Test
  public void jumpToTargetWithinViewRangeShouldntChangeViewRange() {
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
    assertThat(myTimeline.isStreaming()).isFalse();
    // target range is contained in the view range, so view range keeps the same length and there is no need to move.
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(50);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(100);
  }

  @Test
  public void viewRangeChangedWhenJumpingToTargetLargerThanIt() {
    // Give time to make data range non-empty, streaming will update the data range.
    myTimer.tick(TimeUnit.MICROSECONDS.toNanos(300));
    // View range initially: #####
    //         target range:      ##########
    //     View range after:      ##########
    myViewRange.set(50, 100);
    Range targetRange = new Range(100, 200);

    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertThat(myTimeline.isStreaming()).isFalse();
    // target range is larger than view range, so view range zooms out and grows to fit the target, which becomes the new view range.
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(100);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(200);

    // View range initially:      #####
    //         target range: ###############
    //     View range after: ###############
    myViewRange.set(50, 100);
    targetRange.set(0, 150);
    myTimeline.setStreaming(true);
    myTimeline.adjustRangeCloseToMiddleView(targetRange);
    myTimer.tick(TimeUnit.SECONDS.toNanos(10)); // Give plenty of time to animate.
    assertThat(myTimeline.isStreaming()).isFalse();
    // target range is larger than view range (in fact contains the view range) so view range zooms out and grows to fit the target
    assertThat(myViewRange.getMin()).isWithin(DELTA).of(0);
    assertThat(myViewRange.getMax()).isWithin(DELTA).of(150);
  }

  private static long secToUs(long sec) {
    return TimeUnit.SECONDS.toMicros(sec);
  }

  private static long secToNanos(long sec) {
    return TimeUnit.SECONDS.toNanos(sec);
  }
}
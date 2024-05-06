/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.math.DoubleMath;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.NotNull;

/**
 * An implementation of {@link Timeline} that supports moving ranges, e.g. the Studio Profilers.
 */
public final class StreamingTimeline extends AspectModel<StreamingTimeline.Aspect> implements Updatable, Timeline {

  public enum Aspect {
    STREAMING
  }

  /**
   * The view range is multiplied by this value to determine the new view range when zooming.
   */
  public static final double DEFAULT_ZOOM_RATIO = 0.25;

  /**
   * The mid-point of our view used for zooming.
   */
  public static final double ZOOM_MIDDLE_FOCAL_POINT = 0.5;

  /**
   * How many nanoseconds left in our zoom before we just clamp to our final value.
   */
  public static final float ZOOM_LERP_THRESHOLD_NS = 10;

  @VisibleForTesting
  public static final long DEFAULT_VIEW_LENGTH_US = TimeUnit.SECONDS.toMicros(30);

  @NotNull private final Updater myUpdater;
  private final Range myDataRangeUs = new Range(0, 0);
  private final Range myViewRangeUs = new Range(0, 0);
  private final Range mySelectionRangeUs = new Range(); // Empty range
  private final Range myTooltipRangeUs = new Range(); // Empty range

  private boolean myStreaming;

  private float myStreamingFactor;

  /**
   * This range stores the delta between the requested view range and the current view range.
   * Eg: Current View =    |---------|
   * Requested View =        |----|
   * Range in myZoomLeft = |-|    |--| the length between the two segments are stored in min
   * and max respectively.
   * We store the delta in the Range instead of the target view range because we don't want
   * to have more than one point of truth. For example, if we are live and we zoom in/out we
   * would store one value for the zoom, however our live calculation would request another
   * value creating a conflict in which value should be the source of truth. By storing the
   * delta we resolve this by allowing the go live set the view to its requested value
   * then adjusting the min / max values by their computed adjusted amounts.
   */
  private final Range myZoomLeft = new Range(0, 0);

  private final TimelineZoomHelper myZoomHelper = new TimelineZoomHelper(myDataRangeUs, myViewRangeUs, myZoomLeft);

  private boolean myCanStream = true;
  private long myDataStartTimeNs;
  private long myDataLengthNs;
  private boolean myIsReset = false;
  private long myResetTimeNs;
  private boolean myIsPaused = false;
  private long myPausedTime;
  private final Queue<Double> myMouseScrollWheelEvents = new LinkedList<>();
  private final Lock myScrollbarScrollWheelEventLock = new ReentrantLock();

  /**
   * When not negative, interpolates {@link #myViewRangeUs}'s max to it while keeping the view range length.
   */
  private double myTargetRangeMaxUs = -1;

  /**
   * Interpolation factor of the animation that happens when jumping to a target value.
   */
  private float myJumpFactor;

  public StreamingTimeline(@NotNull Updater updater) {
    myUpdater = updater;
    myUpdater.register(this);
  }

  /**
   * Change the streaming mode of this timeline. If canStream is currently set to false (e.g. while user is scrolling or zooming), then
   * nothing happens if the caller tries to enable streaming.
   */
  public void setStreaming(boolean isStreaming) {
    if (!myCanStream && isStreaming) {
      isStreaming = false;
    }

    if (myStreaming == isStreaming) {
      return;
    }
    //noinspection ConstantValue
    assert myCanStream || !isStreaming;

    myStreaming = isStreaming;
    // Update the ranges as if no time has passed.
    update(0);

    changed(Aspect.STREAMING);
  }

  public boolean isStreaming() {
    return myStreaming && !myIsPaused;
  }

  /**
   * Sets whether the timeline can turn on streaming. If this is set to false and the timeline is currently streaming, streaming mode
   * will be toggled off.
   */
  public void setCanStream(boolean canStream) {
    myCanStream = canStream;
    if (!myCanStream && myStreaming) {
      setStreaming(false);
    }
  }

  public boolean canStream() {
    return myCanStream && !myIsPaused;
  }

  public void toggleStreaming() {
    // We cannot clear the zoom because Range#clear sets the min/max values to Double.MIN/MAX.
    myZoomLeft.set(0, 0);
    setStreaming(!isStreaming());
  }

  public boolean isPaused() {
    return myIsPaused;
  }

  public void setIsPaused(boolean paused) {
    myIsPaused = paused;
    if (myIsPaused) {
      myPausedTime = myDataLengthNs;
    }
  }

  public void addMouseScrollWheelEvent(Double event) {
    // Acquire the lock on modifying the mouse scroll wheel events queue.
    myScrollbarScrollWheelEventLock.lock();
    // Store the mouse wheel scroll event.
    myMouseScrollWheelEvents.offer(event);
    // Release the lock.
    myScrollbarScrollWheelEventLock.unlock();
  }

  @NotNull
  @Override
  public Range getDataRange() {
    return myDataRangeUs;
  }

  @NotNull
  @Override
  public Range getViewRange() {
    return myViewRangeUs;
  }

  @NotNull
  @Override
  public Range getSelectionRange() {
    return mySelectionRangeUs;
  }

  @NotNull
  @Override
  public Range getTooltipRange() {
    return myTooltipRangeUs;
  }

  @Override
  public void update(long elapsedNs) {
    handleMouseScrollWheelEvents(); // Does not take into consideration the elapsed time
                                    // after a timeline reset.
    if (myIsReset) {
      // If the timeline has been reset, we need to make sure the elapsed time is the duration between the current update and when reset
      // was triggered. Otherwise we would be adding extra time. e.g.
      //
      // |<----------------  elapsedNs -------------->|
      //                         |<------------------>| // we only want this duration.
      // Last update             Reset                This update
      // |-----------------------r--------------------|
      elapsedNs = myUpdater.getTimer().getCurrentTimeNs() - myResetTimeNs;
      myResetTimeNs = 0;
      myIsReset = false;
    }

    myDataLengthNs += elapsedNs;
    long maxTimelineTimeNs = myDataLengthNs;
    if (myIsPaused) {
      maxTimelineTimeNs = myPausedTime;
    }

    long deviceNowNs = myDataStartTimeNs + maxTimelineTimeNs;
    long deviceNowUs = TimeUnit.NANOSECONDS.toMicros(deviceNowNs);
    myDataRangeUs.setMax(deviceNowUs);
    double viewUs = myViewRangeUs.getLength();
    if (myStreaming) {
      myStreamingFactor = Updater.lerp(myStreamingFactor, 1.0f, 0.95f, elapsedNs, Float.MIN_VALUE);
      double min = Updater.lerp(myViewRangeUs.getMin(), deviceNowUs - viewUs, myStreamingFactor);
      double max = Updater.lerp(myViewRangeUs.getMax(), deviceNowUs, myStreamingFactor);
      myViewRangeUs.set(min, max);
    }
    else {
      myStreamingFactor = 0.0f;
    }
    handleZoomView(elapsedNs);

    handleJumpToTargetMax(elapsedNs);
  }

  /**
   * Handles updating the view range by the delta stored in our {@link #myZoomLeft} value.
   * If we have a delta stored in {@link #myZoomLeft} we apply a percentage of that value to
   * our current view, and reduce the delta currently stored.
   * Eg: View = 10, 100
   * myZoomLeft = 30,-30
   * After we call this function we end up with
   * View = 20, 90
   * myZoomLeft = 20, -20.
   */
  private void handleZoomView(long elapsedNs) {
    if (myZoomLeft.getMin() != 0 || myZoomLeft.getMax() != 0) {
      double min = Updater.lerp(0, myZoomLeft.getMin(), 0.99999f, elapsedNs, ZOOM_LERP_THRESHOLD_NS);
      double max = Updater.lerp(0, myZoomLeft.getMax(), 0.99999f, elapsedNs, ZOOM_LERP_THRESHOLD_NS);
      myZoomLeft.set(myZoomLeft.getMin() - min, myZoomLeft.getMax() - max);
      if (myViewRangeUs.getMax() + max > myDataRangeUs.getMax()) {
        max = myDataRangeUs.getMax() - myViewRangeUs.getMax();
      }
      myViewRangeUs.set(myViewRangeUs.getMin() + min, myViewRangeUs.getMax() + max);
    }
  }

  /**
   * If {@link #myTargetRangeMaxUs} is not negative, interpolates {@link #myViewRangeUs}'s max to it.
   */
  private void handleJumpToTargetMax(long elapsedNs) {
    if (myTargetRangeMaxUs < 0) {
      return; // No need to jump. Return early.
    }

    // Update the view range
    myJumpFactor = Updater.lerp(myJumpFactor, 1.0f, 0.95f, elapsedNs, Float.MIN_VALUE);
    double targetMin = myTargetRangeMaxUs - myViewRangeUs.getLength();
    double min = Updater.lerp(myViewRangeUs.getMin(), targetMin, myJumpFactor);
    double max = Updater.lerp(myViewRangeUs.getMax(), myTargetRangeMaxUs, myJumpFactor);
    myViewRangeUs.set(min, max);

    // Reset the jump factor and myTargetRangeMaxUs when finish jumping to target.
    if (Double.compare(myTargetRangeMaxUs, max) == 0) {
      myTargetRangeMaxUs = -1;
      myJumpFactor = 0.0f;
    }
  }

  /**
   * Makes sure the given target {@link Range} fits {@link #myViewRangeUs}. That means the timeline should zoom out until the view range is
   * bigger than (or equals to) the target range and then shift until the it totally fits the view range.
   * See {@link #adjustRangeCloseToMiddleView(Range)}.
   */
  private void ensureRangeFitsViewRange(@NotNull Range target) {
    // First, zoom out until the target range fits the view range.
    double delta = target.getLength() - myViewRangeUs.getLength();
    if (delta > 0) {
      zoom(delta);
      // If we need to zoom out, it means the target range will occupy the full view range, so the target max should be its max.
      myTargetRangeMaxUs = target.getMax();
    }
    // Otherwise, we move the timeline as little as possible to reach the target range. At this point, there are only two possible
    // scenarios: a) The target range is on the right of the view range, so we adjust the view range relatively to the target's max.
    else if (target.getMax() > myViewRangeUs.getMax()) {
      myTargetRangeMaxUs = target.getMax();
    }
    // b) The target range is on the left of the view range, so we adjust the view range relatively to the target's min.
    else {
      assert target.getMin() < myViewRangeUs.getMin();
      myTargetRangeMaxUs = target.getMin() + myViewRangeUs.getLength();
    }
  }

  /**
   * Adjust the view range to ensure given target is within the {@link #myViewRangeUs}, also try to make the given target is in the middle
   * of the view range. Due to the data range, when the target cannot be displayed in the middle, the view range either starts from zero or
   * ends at the data range max.
   */
  public void adjustRangeCloseToMiddleView(@NotNull Range target) {
    if (target.isEmpty()) {
      return;
    }
    setStreaming(false);
    boolean targetContainedInViewRange = myViewRangeUs.contains(target.getMin()) && myViewRangeUs.contains(target.getMax());
    if (targetContainedInViewRange) {
      // Target already visible. No need to animate to it.
      return;
    }

    ensureRangeFitsViewRange(target);
    boolean isTargetLargerThanViewRange = target.getLength() > myViewRangeUs.getLength();
    if (isTargetLargerThanViewRange) {
      // If the target is larger than the current view range, myTargetRangeMaxUs should have been set to target's max. We shouldn't try to
      // change it at this point, because we'll be zooming out in the next animate cycles. Therefore, we return early.
      return;
    }

    double targetMiddle = (target.getMax() + target.getMin()) / 2;
    double targetMax = targetMiddle + myViewRangeUs.getLength() / 2;
    // When the view range is from timestamp zero, i.e the data range's min, get the view range max value. The view range is the larger one
    // of the previous view length, or the target length if need zooming.
    double maxFromZero = myDataRangeUs.getMin() + Math.max(target.getLength(), myViewRangeUs.getLength());
    // We limit the target max to data range's min, as we can't scroll earlier than timestamp zero.
    targetMax = Math.max(targetMax, maxFromZero);
    // We limit the target max to data range's max, as we can't scroll further than the data.
    myTargetRangeMaxUs = Math.min(targetMax, myDataRangeUs.getMax());
  }

  public void zoom(double deltaUs, double ratio) {
    myZoomHelper.zoom(deltaUs, ratio);
    if (deltaUs < 0.0) {
      if (ratio < 1.0 && myViewRangeUs.getMin() >= myDataRangeUs.getMin()) {
        setStreaming(false);
      }
    }
  }

  /**
   * Zooms out by {@link StreamingTimeline#DEFAULT_ZOOM_RATIO} of the current view range length.
   */
  @Override
  public void zoomOut() {
    zoom(myViewRangeUs.getLength() * DEFAULT_ZOOM_RATIO);
  }

  /**
   * Zooms out by a given amount in microseconds.
   */
  public void zoom(double amountUs) {
    zoom(amountUs, ZOOM_MIDDLE_FOCAL_POINT);
  }

  /**
   * Zooms in by {@link StreamingTimeline#DEFAULT_ZOOM_RATIO} of the current view range length.
   */
  @Override
  public void zoomIn() {
    zoom(-myViewRangeUs.getLength() * DEFAULT_ZOOM_RATIO);
  }

  @Override
  public void resetZoom() {
    // If we are streaming we reset the default zoom keeping our max view aligned with our data max.
    // Otherwise we reset the view using the middle of the current view.
    zoom(DEFAULT_VIEW_LENGTH_US - myViewRangeUs.getLength(), isStreaming() ? 1 : ZOOM_MIDDLE_FOCAL_POINT);
  }

  /**
   * Zoom and pans the view range to the specified target range. See {@link #frameViewToRange(Range, double)}.
   */
  @Override
  public void frameViewToRange(Range targetRangeUs) {
    frameViewToRange(targetRangeUs, 0.1);
  }

  /**
   * Zoom and pans the view range to the specified target range if the range is not point, otherwise center the view range only.
   *
   * @param targetRangeUs target range to lerp view to.
   * @param leftRightPaddingRatio how much space to leave on both sides of the range to leave as padding.
   */
  public void frameViewToRange(Range targetRangeUs, double leftRightPaddingRatio) {
    // Zoom to view when the selection range is not point, otherwise adjust the view range only.
    if (targetRangeUs.isEmpty() || targetRangeUs.isPoint()) {
      adjustRangeCloseToMiddleView(targetRangeUs);
      return;
    }

    setStreaming(false);
    myZoomHelper.updateZoomLeft(targetRangeUs, leftRightPaddingRatio);
  }

  @Override
  public void panView(double deltaUs) {
    if (deltaUs < 0) {
      setStreaming(false);
    }
    Timeline.super.panView(deltaUs);
  }

  /**
   * Handles queued up mouse scroll wheel events stored by the mouse wheel event listener in the TimelineScrollbar.
   */
  public void handleMouseScrollWheelEvents() {
    // Acquire the lock to modify the mouse scroll wheel event queue.
    myScrollbarScrollWheelEventLock.lock();
    // Make a copy of the mouse scroll wheel events queue.
    Queue<Double> copyMouseScrollWheelEvents = new LinkedList<>(myMouseScrollWheelEvents);
    // Clear the original queue.
    myMouseScrollWheelEvents.clear();
    // Release the lock on the mouse scroll wheel events.
    myScrollbarScrollWheelEventLock.unlock();
    // Process the events from the copy of the queue.
    double delta = copyMouseScrollWheelEvents.stream().mapToDouble(Double::doubleValue).sum();
    // If there are no mouse scroll wheel events (delta is 0), do not continue processing.
    if (DoubleMath.fuzzyEquals(delta, 0.0, 0.00001)) {
      return;
    }
    panView(delta);
  }

  /**
   * This function resets the internal state to the timeline.
   *
   * @param startTimeNs the time which should be the 0 value on the timeline (e.g. the beginning of the data range).
   * @param endTimeNs   the current rightmost value on the timeline (e.g. the current max of the data range).
   */
  public void reset(long startTimeNs, long endTimeNs) {
    myDataStartTimeNs = startTimeNs;
    myDataLengthNs = endTimeNs - startTimeNs;
    myIsPaused = false;
    double startTimeUs = TimeUnit.NANOSECONDS.toMicros(startTimeNs);
    double endTimeUs = TimeUnit.NANOSECONDS.toMicros(endTimeNs);
    myDataRangeUs.set(startTimeUs, endTimeUs);
    myViewRangeUs.set(endTimeUs - DEFAULT_VIEW_LENGTH_US, endTimeUs);
    myTargetRangeMaxUs = -1;
    myJumpFactor = 0;
    myZoomLeft.set(0, 0);
    setStreaming(true);
    myResetTimeNs = myUpdater.getTimer().getCurrentTimeNs();
    myIsReset = true;
  }

  public long getDataStartTimeNs() {
    return myDataStartTimeNs;
  }

  /**
   * @param absoluteTimeNs the device time in nanoseconds.
   * @return time relative to the data start time (e.g. zero on the timeline), in microseconds.
   */
  public long convertToRelativeTimeUs(long absoluteTimeNs) {
    return TimeUnit.NANOSECONDS.toMicros(absoluteTimeNs - myDataStartTimeNs);
  }
}

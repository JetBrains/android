/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.atrace;

import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.event.EventAction;
import org.jetbrains.annotations.NotNull;
import trebuchet.model.Model;
import trebuchet.model.SchedSlice;
import trebuchet.model.ThreadModel;
import trebuchet.model.base.SliceGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An atrace frame represents all events that happen on the main thread, and render thread between
 * the Choreographer#doFrame event on the main thread and the DrawFrame event on the render thread.
 * Each frame implements parts of {@link DurationData} and {@link EventAction} for easy use
 * in UI components. The frame is a container and is produced by {@link AtraceFrameFactory}.
 */
public class AtraceFrame extends EventAction<AtraceFrame.PerfClass> implements DurationData {
  private static final double SECONDS_TO_US = TimeUnit.SECONDS.toMicros(1);
  // TODO(b/76205567): Make this a configuration parameter.
  private static final double EXPECTED_FRAME_TIME_SECONDS = 1 / 60.0;

  /**
   * A rating for this frame's performance, based against some expected time. (If you can add more
   * information about where expected time comes from, that would be great)
   *
   * NOTE: The values are the same as used by systrace itself. See also
   * https://github.com/catapult-project/catapult/blob/643994eea8e5d5421441168190e8499c9cbf7ae8/tracing/tracing/model/frame.html
   */
  enum PerfClass {
    /**
     * A performance rating for when a frame runs under an expected time.
     */
    GOOD,
    /**
     * A performance rating for when a frame runs over expected time, but not so much as to be {@link TERRIBLE}
     */
    BAD,
    /**
     * A performance rating for when a frame runs 2x or more over expected time.
     */
    TERRIBLE
  }

  /**
   * Range that represents the absolute min and max times of all ranges contained within this frame.
   */
  @NotNull
  private final Range myTotalRangeSeconds;

  /**
   * Range that represents the absolute min and max times of the render thread slices.
   */
  @NotNull
  private final Range myRenderThreadRangeSeconds;

  /**
   * Range that represents the absolute min and max times of the main thread slices.
   */
  @NotNull
  private final Range myMainThreadRangeSeconds;

  /**
   * List of all slices that represent elements on the main thread, and render thread contained within this frames time.
   */
  @NotNull
  private final List<SliceGroup> mySlices;

  /**
   * List of all thread schedule events that change thread state for the main thread and render thread within the time of this frame.
   */
  @NotNull
  private final List<SchedSlice> mySchedSlice;

  /**
   * List of ranges mapping to each high level slice contained in the {@code mySlices} list.
   */
  @NotNull
  private final List<Range> myRanges;

  /**
   * Model used to convert boot time (timing of slices) to mono clock time (timing used in studio).
   */
  @NotNull
  private final Model myModel;

  /**
   * Total cpu time in seconds this frame was scheduled for.
   */
  private double myCpuTimeSeconds;

  /**
   * Constructs a basic frame that has no slice information.
   *
   * @param model the model used to convert slice time ranges from device boot time to process mono clock time.
   */
  public AtraceFrame(@NotNull Model model) {
    super(0, 0, PerfClass.GOOD);
    myTotalRangeSeconds = new Range();
    myRenderThreadRangeSeconds = new Range();
    myMainThreadRangeSeconds = new Range();
    mySlices = new ArrayList<>();
    mySchedSlice = new ArrayList<>();
    myRanges = new ArrayList<>();
    myModel = model;
    myCpuTimeSeconds = 0;
  }

  /**
   * Depending on this frame's length, this method returns the corresponding {@link PerfClass}.
   *
   * NOTE: This function uses the same logic as systrace. See also
   * https://github.com/catapult-project/catapult/blob/344aee7e135aefe8ed8312dea6191873e00a1931/tracing/tracing/extras/android/android_auditor.html#L434
   *
   * @return the {@link PerfClass} for this object.
   */
  @NotNull
  public PerfClass getPerfClass() {
    // This is just a timing estimation, good enough for most scenarios.
    // Note: Some additional work we could do here if users request it are along the lines of comparing this frame timing
    // to that of the VSYNC-app.
    // https://github.com/catapult-project/catapult/blob/f1d78e8c269b179b78db1d1fcb600481c5f781f6/tracing/tracing/model/helpers/android_surface_flinger.html
    Range rangeUs = getTotalRangeSeconds();
    if (rangeUs.getLength() > EXPECTED_FRAME_TIME_SECONDS) {
      return PerfClass.BAD;
    }
    else {
      return PerfClass.GOOD;
    }
  }

  /**
   * @return absolute time of this frame in micro seconds.
   */
  @Override
  public long getDuration() {
    return (long)(SECONDS_TO_US * getTotalRangeSeconds().getLength());
  }

  /**
   * @return absolute min time of this frame in micro seconds.
   */
  @Override
  public long getStartUs() {
    return (long)convertToProcessTimeUs(myTotalRangeSeconds.getMin());
  }

  /**
   * @return absolute max time of this frame in micro seconds.
   */
  @Override
  public long getEndUs() {
    return (long)convertToProcessTimeUs(myTotalRangeSeconds.getMax());
  }

  /**
   * @return Performance class of this frame based on frame length.
   */
  @NotNull
  @Override
  public PerfClass getType() {
    return getPerfClass();
  }

  /**
   * @return total absolute range of this frame in seconds.
   */
  @NotNull
  public Range getTotalRangeSeconds() {
    return myTotalRangeSeconds;
  }

  /**
   * @return total range of this frame converted to process time micro seconds
   */
  @NotNull
  public Range getTotalRangeProcessTime() {
    return new Range(convertToProcessTimeUs(myTotalRangeSeconds.getMin()), convertToProcessTimeUs(myTotalRangeSeconds.getMax()));
  }

  /**
   * @return total range of this frames render thread converted to process time micro seconds
   */
  @NotNull
  public Range getRenderThreadRangeProcessTime() {
    if (!myRenderThreadRangeSeconds.isEmpty()) {
      return new Range(convertToProcessTimeUs(myRenderThreadRangeSeconds.getMin()), convertToProcessTimeUs(myRenderThreadRangeSeconds.getMax()));
    }
    return new Range(0, 0);
  }

  /**
   * @return total range of this frames main thread converted to process time micro seconds
   */
  @NotNull
  public Range getUIThreadRangeProcessTime() {
    if (!myMainThreadRangeSeconds.isEmpty()) {
      return new Range(convertToProcessTimeUs(myMainThreadRangeSeconds.getMin()), convertToProcessTimeUs(myMainThreadRangeSeconds.getMax()));
    }
    return new Range(0, 0);
  }

  /**
   * @return list of slices that represent all slices in this frame
   */
  @NotNull
  public List<SliceGroup> getSlices() {
    return mySlices;
  }

  /**
   * @return total cpu time this frame was scheduled in seconds.
   */
  public double getCpuTimeSeconds() {
    return myCpuTimeSeconds;
  }

  /**
   * @return list of all slice ranges contained within this frame.
   */
  @NotNull
  public List<Range> getRanges() {
    return myRanges;
  }

  /**
   * @return list of thread scheduling changes for the render thread and main thread that happen within this frame.
   */
  @NotNull
  public List<SchedSlice> getSchedSlice() {
    return mySchedSlice;
  }

  /**
   * Helper function to convert from seconds device boot time to process time micro seconds.
   *
   * @param offsetTime time in seconds greater than or equal to model begin timestamp.
   * @return offsetTime converted to process time micro seconds.
   */
  private double convertToProcessTimeUs(double offsetTime) {
    return SECONDS_TO_US * ((offsetTime - myModel.getBeginTimestamp()) + myModel.getParentTimestamp());
  }

  /**
   * Function called by the {@link AtraceFrameFactory} to add slices to the frame that fall within the scope of the frame.
   *
   * @param sliceGroup Top level slice group that occurs within this frame.
   * @param range      Range of the sliceGroup.
   * @param thread     Which thread the sliceGroup occurs on.
   */
  public void addSlice(@NotNull SliceGroup sliceGroup, @NotNull Range range, @NotNull ThreadModel thread) {
    mySlices.add(sliceGroup);
    myRanges.add(range);
    myTotalRangeSeconds.setMin(Math.min(myTotalRangeSeconds.getMin(), range.getMin()));
    myTotalRangeSeconds.setMax(Math.max(myTotalRangeSeconds.getMax(), range.getMax()));
    mySchedSlice.addAll(thread.getSchedSlices());
    myCpuTimeSeconds += sliceGroup.getCpuTime();

    // Keep events from the main thread, and render thread separated for later analysis.
    // Each thread is optionally passed in. The main thread may be passed in more than once.
    // The render thread will be passed in exactly once.
    if (thread.getName().equals(AtraceFrameFactory.RENDER_THREAD_NAME)) {
      assert myRenderThreadRangeSeconds.isEmpty();
      myRenderThreadRangeSeconds.set(range.getMin(), range.getMax());
    }
    else if (myMainThreadRangeSeconds.isEmpty()) {
      myMainThreadRangeSeconds.set(range.getMin(), range.getMax());
    }
  }
}
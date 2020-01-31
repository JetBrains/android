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
import com.intellij.util.Function;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import trebuchet.model.base.SliceGroup;

import java.util.concurrent.TimeUnit;

/**
 * An atrace frame represents all events that happen on a specified thread,
 * Each frame implements parts of {@link DurationData} for easy use in UI components.
 * The frame is a container and is produced by {@link AtraceFrameManager}.
 */
public class AtraceFrame extends EventAction<AtraceFrame.PerfClass> implements DurationData {
  private static final double SECONDS_TO_US = TimeUnit.SECONDS.toMicros(1);
  public static final AtraceFrame EMPTY = new AtraceFrame(0, (t) -> 0L, 0, FrameThread.OTHER);

  /**
   * A rating for this frame's performance, based against some expected time. The expected time
   * is configured in the constructor of the frame.
   */
  public enum PerfClass {
    /**
     * When no slices have been added to this frame.
     */
    NOT_SET,
    /**
     * A performance rating for when a frame runs under an expected time.
     */
    GOOD,
    /**
     * A performance rating for when a frame runs over expected time.
     */
    BAD,
  }

  public enum FrameThread {
    MAIN,
    RENDER,
    OTHER,
  }

  /**
   * Links to the frame associated with this frame.
   * If this frame is a main thread frame, then the associated frame is the render thread frame that is created by this frame.
   * If this frame is a render thread frame, then the associated frame is the main thread frame that created this render thread frame.
   */
  private AtraceFrame myAssociatedFrame;

  /**
   * Range that represents the absolute min and max times of all ranges contained within this frame.
   */
  @NotNull
  private final Range myTotalRangeSeconds;

  /**
   * Function used to convert boot time (timing of slices) to mono clock time (timing used in studio).
   */
  @NotNull
  private final Function<Double, Long> myBootClockSecondsToMonoUs;

  /**
   * Total cpu time in seconds this frame was scheduled for.
   */
  private double myCpuTimeSeconds;

  /**
   * Time set in the constructor to indicate that any frames longer than this value will be marked as {@link PerfClass#BAD}.
   */
  private final long myLongFrameTimeUs;

  @NotNull
  private PerfClass myPerfClass;

  private final int myThreadId;

  private final FrameThread myThread;

  private final List<SliceGroup> mySlices;

  /**
   * Constructs a basic frame that has no slice information.
   */
  public AtraceFrame(int threadId, @NotNull Function<Double, Long> bootClockSecondsToMonoUs, long longFrameTimeUs, FrameThread thread) {
    super(0, 0, PerfClass.NOT_SET);
    myTotalRangeSeconds = new Range();
    myBootClockSecondsToMonoUs = bootClockSecondsToMonoUs;
    myLongFrameTimeUs = longFrameTimeUs;
    myThreadId = threadId;
    myPerfClass = PerfClass.NOT_SET;
    myThread = thread;
    mySlices = new ArrayList<>();
  }

  public FrameThread getThread() {
    return myThread;
  }

  public void setAssociatedFrame(AtraceFrame associatedFrame) {
    myAssociatedFrame = associatedFrame;
  }

  public AtraceFrame getAssociatedFrame() {
    return myAssociatedFrame;
  }

  /**
   * Depending on this frame's length, this method returns the corresponding {@link PerfClass}.
   */
  @NotNull
  public PerfClass getPerfClass() {
    return myPerfClass;
  }

  public PerfClass getTotalPerfClass() {
    if (myPerfClass == PerfClass.NOT_SET) {
      return PerfClass.NOT_SET;
    }
    double associatedFrameLength = myAssociatedFrame == null ? 0.0 : myAssociatedFrame.getTotalRangeSeconds().getLength();
    if (SECONDS_TO_US * (associatedFrameLength + myTotalRangeSeconds.getLength()) > myLongFrameTimeUs) {
      return PerfClass.BAD;
    }
    return PerfClass.GOOD;
  }

  /**
   * @return absolute time of this frame in micro seconds.
   */
  @Override
  public long getDurationUs() {
    return (long)(SECONDS_TO_US * getTotalRangeSeconds().getLength());
  }

  /**
   * @return absolute min time of this frame in micro seconds.
   */
  @Override
  public long getStartUs() {
    return myBootClockSecondsToMonoUs.fun(myTotalRangeSeconds.getMin());
  }

  /**
   * @return absolute max time of this frame in micro seconds.
   */
  @Override
  public long getEndUs() {
    return myBootClockSecondsToMonoUs.fun(myTotalRangeSeconds.getMax());
  }

  /**
   * @return total cpu time this frame was scheduled in seconds.
   */
  public double getCpuTimeSeconds() {
    return myCpuTimeSeconds;
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

  public int getThreadId() {
    return myThreadId;
  }

  /**
   * Function called by the {@link AtraceFrameManager} to add slices to the frame that fall within the scope of the frame.
   *
   * @param sliceGroup Top level slice group that occurs within this frame.
   * @param range      Range of the sliceGroup.
   */
  public void addSlice(@NotNull SliceGroup sliceGroup, @NotNull Range range) {
    mySlices.add(sliceGroup);
    myTotalRangeSeconds.setMin(Math.min(myTotalRangeSeconds.getMin(), range.getMin()));
    myTotalRangeSeconds.setMax(Math.max(myTotalRangeSeconds.getMax(), range.getMax()));
    myCpuTimeSeconds += sliceGroup.getCpuTime();
    if (SECONDS_TO_US * myTotalRangeSeconds.getLength() > myLongFrameTimeUs) {
      myPerfClass = PerfClass.BAD;
    }
    else {
      myPerfClass = PerfClass.GOOD;
    }
  }

  public List<SliceGroup> getSlices() {
    return mySlices;
  }
}
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
package com.android.tools.profilers.cpu.systemtrace;

import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.event.EventAction;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

/**
 * An atrace frame represents all events that happen on a specified thread,
 * Each frame implements parts of {@link DurationData} for easy use in UI components.
 * The frame is a container and is produced by {@link SystemTraceFrameManager}.
 */
public class SystemTraceFrame extends EventAction<SystemTraceFrame.PerfClass> implements DurationData {
  public static final SystemTraceFrame EMPTY = new SystemTraceFrame(0, 0, 0, 0, FrameThread.OTHER);

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

  /**
   * Events that occur on the main thread to define when a frame starts. These events changed in M, and System Tracing in profilers
   * is only supporting O+ devices however to keep this class in sync with the systrace sibling keeping both here for reference.
   */
  private static final String APP_MAIN_THREAD_FRAME_ID_MPLUS = "Choreographer#doFrame";
  private static final String APP_RENDER_THREAD_FRAME_ID_MPLUS = "(DrawFrame|doFrame|queueBuffer)";

  public enum FrameThread {
    MAIN(APP_MAIN_THREAD_FRAME_ID_MPLUS),
    RENDER(APP_RENDER_THREAD_FRAME_ID_MPLUS),
    OTHER("");

    private final String myIdentifierRegEx;

    /**
     * Returns a regular expression that matches the frames names for this thread type.
     */
    public String getIdentifierRegEx() {
      return myIdentifierRegEx;
    }

    FrameThread(String identifierRegEx) {
      myIdentifierRegEx = identifierRegEx;
    }
  }

  /**
   * Links to the frame associated with this frame.
   * If this frame is a main thread frame, then the associated frame is the render thread frame that is created by this frame.
   * If this frame is a render thread frame, then the associated frame is the main thread frame that created this render thread frame.
   */
  private SystemTraceFrame myAssociatedFrame;

  /**
   * Total cpu time in seconds this frame was scheduled for.
   */
  private final double myCpuTimeUs;

  /**
   * Time set in the constructor to indicate that any frames longer than this value will be marked as {@link PerfClass#BAD}.
   */
  private final long myLongFrameTimeUs;

  @NotNull
  private final PerfClass myPerfClass;

  private final FrameThread myThread;

  public SystemTraceFrame(@NotNull TraceEventModel eventModel,
                          long longFrameTimeUs,
                          FrameThread thread) {
    this(eventModel.getStartTimestampUs(),
         eventModel.getEndTimestampUs(),
         eventModel.getCpuTimeUs(),
         longFrameTimeUs,
         thread);
  }

  @VisibleForTesting
  public SystemTraceFrame(long startUs,
                          long endUs,
                          double cpuTimeUs,
                          long longFrameTimeUs,
                          FrameThread thread) {
    super(startUs, endUs, PerfClass.NOT_SET);
    myCpuTimeUs = cpuTimeUs;
    myLongFrameTimeUs = longFrameTimeUs;
    myThread = thread;

    if (getDurationUs() > myLongFrameTimeUs) {
      myPerfClass = PerfClass.BAD;
    } else {
      myPerfClass = PerfClass.GOOD;
    }
  }

  public FrameThread getThread() {
    return myThread;
  }

  public void setAssociatedFrame(SystemTraceFrame associatedFrame) {
    myAssociatedFrame = associatedFrame;
  }

  public SystemTraceFrame getAssociatedFrame() {
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
    if (this == EMPTY || myPerfClass == PerfClass.NOT_SET) {
      return PerfClass.NOT_SET;
    }
    double associatedFrameLengthUs = myAssociatedFrame == null ? 0.0 : myAssociatedFrame.getDurationUs();
    if (associatedFrameLengthUs + getDurationUs() > myLongFrameTimeUs) {
      return PerfClass.BAD;
    }
    return PerfClass.GOOD;
  }

  /**
   * @return absolute time of this frame in micro seconds.
   */
  @Override
  public long getDurationUs() {
    return getEndUs() - getStartUs();
  }

  /**
   * @return total cpu time this frame was scheduled in seconds.
   */
  public double getCpuTimeUs() {
    return myCpuTimeUs;
  }

  /**
   * @return Performance class of this frame based on frame length.
   */
  @NotNull
  @Override
  public PerfClass getType() {
    return getPerfClass();
  }
}
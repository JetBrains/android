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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.cpu.CpuFramesModel;
import com.google.common.annotations.VisibleForTesting;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import trebuchet.model.ProcessModel;
import trebuchet.model.ThreadModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * This class builds {@link AtraceFrame}s for each {@code AtraceFrame.FrameThread} types.
 */
public class AtraceFrameManager {

  @NotNull
  private final Function<Double, Long> myBootClockSecondsToMonoUs;

  private final List<AtraceFrame> myMainThreadFrames;
  private final List<AtraceFrame> myRenderThreadFrames;

  /**
   * Constructs a default manager, the constructor finds the main thread and will assert if one is not found.
   *
   * @param process Process used to find the main and render threads.
   * @param bootClockSecondsToMonoUs function to convert trace boot time in seconds to mono time micros.
   * @param renderThreadId The id of the render thread
   */
  public AtraceFrameManager(@NotNull ProcessModel process, @NotNull Function<Double, Long> bootClockSecondsToMonoUs, int renderThreadId) {
    myBootClockSecondsToMonoUs = bootClockSecondsToMonoUs;
    myMainThreadFrames = buildFramesList(AtraceFrame.FrameThread.MAIN, process, process.getId());
    myRenderThreadFrames = buildFramesList(AtraceFrame.FrameThread.RENDER, process, renderThreadId);
    findAssociatedFrames();
  }

  /**
   * Finds main thread and render thread frames that are associated with each other and adds a link to each one in the other.
   */
  private void findAssociatedFrames() {
    int mainFramesIterator = 0, renderFramesIterator = 0;

    while (mainFramesIterator < myMainThreadFrames.size() && renderFramesIterator < myRenderThreadFrames.size()) {
      AtraceFrame mainThreadFrame = myMainThreadFrames.get(mainFramesIterator);
      AtraceFrame renderThreadFrame = myRenderThreadFrames.get(renderFramesIterator);
      if (renderThreadFrame == AtraceFrame.EMPTY || renderThreadFrame.getEndUs() < mainThreadFrame.getEndUs()) {
        renderFramesIterator++;
      }
      else if (mainThreadFrame == AtraceFrame.EMPTY ||
               renderThreadFrame.getStartUs() > mainThreadFrame.getEndUs() ||
               renderThreadFrame.getStartUs() < mainThreadFrame.getStartUs()) {
        mainFramesIterator++;
      }
      else {
        mainThreadFrame.setAssociatedFrame(renderThreadFrame);
        renderThreadFrame.setAssociatedFrame(mainThreadFrame);
        mainFramesIterator++;
        renderFramesIterator++;
      }
    }
  }

  @NotNull
  private List<AtraceFrame> buildFramesList(AtraceFrame.FrameThread frameThread,
                                            ProcessModel processModel,
                                            int threadId) {
    List<AtraceFrame> frames = new ArrayList<>();
    Optional<ThreadModel> activeThread = processModel.getThreads().stream().filter((thread) -> thread.getId() == threadId).findFirst();
    if (!activeThread.isPresent()) {
      return frames;
    }
    new SliceStream(activeThread.get().getSlices()).matchPattern(Pattern.compile(frameThread.getIdentifierRegEx())).enumerate((sliceGroup) -> {
      AtraceFrame frame = new AtraceFrame(activeThread.get().getId(), myBootClockSecondsToMonoUs, CpuFramesModel.SLOW_FRAME_RATE_US, frameThread);
      double startTime = sliceGroup.getStartTime();
      double endTime = sliceGroup.getEndTime();
      frame.addSlice(sliceGroup, new Range(startTime, endTime));
      frames.add(frame);
      return SliceStream.EnumerationResult.SKIP_CHILDREN;
    });
    return frames;
  }

  /**
   * Returns a list of frames for a frame type.
   */
  @VisibleForTesting
  List<AtraceFrame> getFramesList(@NotNull AtraceFrame.FrameThread thread) {
    switch (thread) {
      case MAIN:
        return myMainThreadFrames;
      case RENDER:
        return myRenderThreadFrames;
      default:
        return new ArrayList<>();
    }
  }

  /**
   * Returns a series of frames where gaps between frames are filled with empty frames. This allows the caller to determine the
   * frame length by looking at the delta between a valid frames series and the empty frame series that follows it. The delta between
   * an empty frame series and the following frame is idle time between frames.
   *
   * This only supports {@code AtraceFrame.FrameThread.MAIN} and {@code AtraceFrame.FrameThread.RENDER}. Will return an empty list for
   * {@code AtraceFrame.FrameThread.OTHER}.
   */
  @NotNull
  public List<SeriesData<AtraceFrame>> getFrames(@NotNull AtraceFrame.FrameThread thread) {
    List<SeriesData<AtraceFrame>> framesSeries = new ArrayList<>();
    List<AtraceFrame> framesList = getFramesList(thread);
    // Look at each frame converting them to series data.
    // The last frame is handled outside the for loop as we need to add an entry for the frame as well as an entry for the frame ending.
    // Single frames are handled in the last frame case.
    for (int i = 1; i < framesList.size(); i++) {
      AtraceFrame current = framesList.get(i);
      AtraceFrame past = framesList.get(i - 1);
      framesSeries.add(new SeriesData<>(myBootClockSecondsToMonoUs.apply(past.getTotalRangeSeconds().getMin()), past));

      // Need to get the time delta between two frames.
      // If we have a gap then we add an empty frame to signify to the UI that nothing should be rendered.
      if (past.getTotalRangeSeconds().getMax() < current.getTotalRangeSeconds().getMin()) {
        framesSeries.add(new SeriesData<>(myBootClockSecondsToMonoUs.apply(past.getTotalRangeSeconds().getMax()), AtraceFrame.EMPTY));
      }
    }

    // Always add the last frame, and a null frame following to properly setup the series for the UI.
    if (!framesList.isEmpty()) {
      AtraceFrame lastFrame = framesList.get(framesList.size() - 1);
      framesSeries.add(new SeriesData<>(myBootClockSecondsToMonoUs.apply(lastFrame.getTotalRangeSeconds().getMin()), lastFrame));
      framesSeries.add(new SeriesData<>(myBootClockSecondsToMonoUs.apply(lastFrame.getTotalRangeSeconds().getMax()), AtraceFrame.EMPTY));
    }
    return framesSeries;
  }
}

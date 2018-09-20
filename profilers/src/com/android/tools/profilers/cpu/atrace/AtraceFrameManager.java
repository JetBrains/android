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
import com.android.tools.profilers.cpu.CpuFramesModel;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import trebuchet.model.ProcessModel;
import trebuchet.model.ThreadModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * This class builds {@link AtraceFrame} using a {@link AtraceFrameFilterConfig}
 */
public class AtraceFrameManager {

  @NotNull
  private final Function<Double, Long> myBootClockSecondsToMonoUs;

  /**
   * Container to hold UI and thread threads for processing.
   */
  @NotNull
  private final ProcessModel myProcessModel;

  private final int myRenderThreadId;

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
    myProcessModel = process;
    myRenderThreadId = renderThreadId;
    myMainThreadFrames =
      getFramesList(AtraceFrameFilterConfig.APP_MAIN_THREAD_FRAME_ID_MPLUS, myProcessModel.getId(), CpuFramesModel.SLOW_FRAME_RATE_US,
                    AtraceFrame.FrameThread.MAIN);
    myRenderThreadFrames =
      getFramesList(AtraceFrameFilterConfig.APP_RENDER_THREAD_FRAME_ID_MPLUS, myRenderThreadId, CpuFramesModel.SLOW_FRAME_RATE_US,
                    AtraceFrame.FrameThread.RENDER);
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
  private List<AtraceFrame> getFramesList(String identifierRegEx,
                                          int threadId,
                                          long longFrameTimingUs,
                                          AtraceFrame.FrameThread frameThread) {
    List<AtraceFrame> frames = new ArrayList<>();
    Optional<ThreadModel> activeThread = myProcessModel.getThreads().stream().filter((thread) -> thread.getId() == threadId).findFirst();
    if (!activeThread.isPresent()) {
      return frames;
    }
    new SliceStream(activeThread.get().getSlices()).matchPattern(Pattern.compile(identifierRegEx)).enumerate((sliceGroup) -> {
      AtraceFrame frame = new AtraceFrame(activeThread.get().getId(), myBootClockSecondsToMonoUs, longFrameTimingUs, frameThread);
      double startTime = sliceGroup.getStartTime();
      double endTime = sliceGroup.getEndTime();
      frame.addSlice(sliceGroup, new Range(startTime, endTime));
      frames.add(frame);
      return true;
    });
    return frames;
  }

  /**
   * @return This function return a list of frames that match the given filter.
   */
  @NotNull
  public List<AtraceFrame> buildFramesList(@NotNull AtraceFrameFilterConfig filter) {
    if (filter.getThreadId() == myProcessModel.getId() &&
        filter.getIdentifierRegEx() == AtraceFrameFilterConfig.APP_MAIN_THREAD_FRAME_ID_MPLUS &&
        filter.getLongFrameTimingUs() ==
        CpuFramesModel.SLOW_FRAME_RATE_US) {
      return myMainThreadFrames;
    }
    if (filter.getThreadId() == myRenderThreadId &&
        filter.getIdentifierRegEx() == AtraceFrameFilterConfig.APP_RENDER_THREAD_FRAME_ID_MPLUS &&
        filter.getLongFrameTimingUs() ==
        CpuFramesModel.SLOW_FRAME_RATE_US) {
      return myRenderThreadFrames;
    }
    return getFramesList(filter.getIdentifierRegEx(), filter.getThreadId(), filter.getLongFrameTimingUs(),
                         filter.getThreadId() == myProcessModel.getId()
                         ? AtraceFrame.FrameThread.MAIN
                         : (filter.getThreadId() == myRenderThreadId ? AtraceFrame.FrameThread.RENDER : AtraceFrame.FrameThread.OTHER));
  }
}

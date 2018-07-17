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
public class AtraceFrameFactory {

  @NotNull
  private final Function<Double, Long> myBootClockSecondsToMonoUs;

  /**
   * Container to hold ui and thread threads for processing.
   */
  @NotNull
  private final ProcessModel myProcessModel;

  /**
   * Constructs a default factory, the constructor finds the main thread and will assert if one is not found.
   *
   * @param process Process used to find the main and render threads.
   * @param bootClockSecondsToMonoUs function to convert trace boot time in seconds to mono time micros.
   */
  public AtraceFrameFactory(@NotNull ProcessModel process, @NotNull Function<Double, Long> bootClockSecondsToMonoUs) {
    myBootClockSecondsToMonoUs = bootClockSecondsToMonoUs;
    myProcessModel = process;
  }

  /**
   * @return This function return a list of frames that match the given filter.
   */
  @NotNull
  public List<AtraceFrame> buildFramesList(@NotNull AtraceFrameFilterConfig filter) {
    List<AtraceFrame> frames = new ArrayList<>();
    Optional<ThreadModel> activeThread =
      myProcessModel.getThreads().stream().filter((thread) -> thread.getId() == filter.getThreadId()).findFirst();
    if (!activeThread.isPresent()) {
      return frames;
    }
    new SliceStream(activeThread.get().getSlices()).matchPattern(Pattern.compile(filter.getIdentifierRegEx())).enumerate((sliceGroup) -> {
      AtraceFrame frame = new AtraceFrame(activeThread.get().getId(), myBootClockSecondsToMonoUs, filter.getLongFrameTimingUs());
      double startTime = sliceGroup.getStartTime();
      double endTime = sliceGroup.getEndTime();
      frame.addSlice(sliceGroup, new Range(startTime, endTime));
      frames.add(frame);
      return true;
    });
    return frames;
  }
}

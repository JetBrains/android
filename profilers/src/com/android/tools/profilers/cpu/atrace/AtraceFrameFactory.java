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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import trebuchet.model.Model;
import trebuchet.model.ProcessModel;
import trebuchet.model.ThreadModel;
import trebuchet.model.base.SliceGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class builds {@link AtraceFrame} using several hard coded trace events found in the android platform.
 * The trace events are seperated by main thread and render thread. A frame is defined to start with a "Choreographer#doFrame" event
 * and continue until an associated "DrawFrame" event is found.
 * The "Choreographer#doFrame" is located on the main thread, while the "DrawFrame" is found on the render thread.
 * If no render thread is found (as is the case with native apps) a frame is defined only as the events that occur within the
 * "Choreographer#doFrame" event range.
 */
public class AtraceFrameFactory {
  /**
   * The platform RenderThread is hard coded to have this name.
   */
  public static final String RENDER_THREAD_NAME = "RenderThread";

  private static final String[] RENDER_THREAD_SYNC_ELEMENTS = {
    RenderThreadDrawNames.RENDER_THREAD_QUEUE.getDrawName(),
    RenderThreadDrawNames.RENDER_THREAD_SWAP.getDrawName()
  };

  /**
   * Events that occur on the main thread to define when a frame starts. These events changed in M, and System Tracing in profilers
   * is only supporting O+ devices however to keep this class in sync with the systrace sibling keeping both here for reference.
   */
  public enum UIThreadDrawType {
    LEGACY("performTraversals"),
    MARSHMALLOW("Choreographer#doFrame");

    @NotNull private final String myDrawName;

    UIThreadDrawType(@NotNull String drawName) {
      myDrawName = drawName;
    }

    @NotNull
    public String getDrawName() {
      return myDrawName;
    }
  }

  /**
   * On the render thread we have a few events that tell us when a frame ends as well as one event that tells us when we are doing
   * a frame sync. These elements are based on the systrace implementation
   * https://github.com/catapult-project/catapult/blob/f1d78e8c269b179b78db1d1fcb600481c5f781f6/tracing/tracing/model/helpers/android_app.html
   */
  public enum RenderThreadDrawNames {
    RENDER_THREAD("DrawFrame"),
    RENDER_THREAD_INDEP("doFrame"),
    RENDER_THREAD_QUEUE("queueBuffer"),
    RENDER_THREAD_SWAP("eglSwapBuffers"),
    THREAD_SYNC("syncFrameState");

    @NotNull private final String myDrawName;

    RenderThreadDrawNames(@NotNull String drawName) {
      myDrawName = drawName;
    }

    @NotNull
    public String getDrawName() {
      return myDrawName;
    }
  }

  /**
   * Container for ui and render threads needed for creating frames.
   */
  private static final class AtraceThreads {
    /**
     * ThreadModel that represents the main thread. This thread has the same name as our process.
     */
    @NotNull
    private final ThreadModel myUiThread;
    /**
     * ThreadModel that represents our render thread. This thread has a hardcoded name and is optional.
     */
    @Nullable
    private final ThreadModel myRenderThread;

    @NotNull
    public ThreadModel getUiThread() {
      return myUiThread;
    }

    @Nullable
    public ThreadModel getRenderThread() {
      return myRenderThread;
    }

    AtraceThreads(@NotNull ThreadModel uiThread, @Nullable ThreadModel renderThread) {
      myUiThread = uiThread;
      myRenderThread = renderThread;
    }
  }

  /**
   * Model used to parse the frame information. This is used to convert device boot time to process mono clock time.
   */
  @NotNull
  private Model myModel;

  /**
   * Container to hold ui and thread threads for processing.
   */
  @NotNull
  private final AtraceThreads myThreads;

  /**
   * Constructs a default factory, the constructor finds the main thread and will assert if one is not found.
   *
   * @param model   Model used to parse frame information.
   * @param process Process used to find the main and render threads.
   */
  public AtraceFrameFactory(@NotNull Model model, @NotNull ProcessModel process) {
    myModel = model;
    myThreads = findUiAndRenderThread(process);
  }

  /**
   * Helper function used to find the main and render threads.
   *
   * @return ui thread model as this element is required to be non-null.
   */
  @NotNull
  private AtraceThreads findUiAndRenderThread(@NotNull ProcessModel process) {
    ThreadModel uiThread = null;
    ThreadModel renderThread = null;
    for (ThreadModel thread : process.getThreads()) {
      if (thread.getId() == process.getId()) {
        uiThread = thread;
      }
      else if (thread.getName().equals(RENDER_THREAD_NAME)) {
        renderThread = thread;
      }
      if (uiThread != null && renderThread != null) {
        break;
      }
    }
    assert uiThread != null;
    return new AtraceThreads(uiThread, renderThread);
  }

  /**
   * @return This function finds all UI and Render thread frames.
   */
  public List<AtraceFrame> buildFramesList() {
    List<AtraceFrame> frames = getUiThreadDrivenFrames();
    frames.addAll(getRenderThreadDrivenFrames());
    return frames;
  }

  /**
   * Function to find frames on the render thread that overlap the main thread. This is done by finding children of the render thread
   * whos range overlaps the main threads slice. Then iterating those children finding frames that match the
   * {@link RenderThreadDrawNames:THREAD_SYNC} name.
   *
   * @param reference main thread slice. The range from this slice is used to find render thread events.
   * @return {@link SliceGroup} that overlaps the reference slice with the thread name matching {@link RenderThreadDrawNames:THREAD_SYNC}
   */
  private SliceGroup findOverlappingDrawFrame(SliceGroup reference) {
    // of all top level renderthread slices, find the one that has a 'sync'
    // within the uiDrawSlice
    if (myThreads.getRenderThread() == null) {
      return null;
    }
    // Setup an array used to store matching slice.
    final SliceGroup[] slices = new SliceGroup[1];
    new SliceStream(myThreads.getRenderThread().getSlices())
      .matchName(RenderThreadDrawNames.RENDER_THREAD.getDrawName())
      .overlapsRange(new Range(reference.getStartTime(), reference.getEndTime()))
      .enumerate(
        (sliceGroup) -> {
          SliceGroup threadSyncSlice =
            new SliceStream(sliceGroup.getChildren()).matchName(RenderThreadDrawNames.THREAD_SYNC.getDrawName()).findFirst();
          if (threadSyncSlice != null &&
              threadSyncSlice.getStartTime() >= reference.getStartTime() &&
              threadSyncSlice.getEndTime() <= reference.getEndTime()) {
            slices[0] = sliceGroup;
            // If we found a matching slice return false because we do not need to keep looking.
            return false;
          }
          else {
            return true;
          }
        });
    return slices[0];
  }

  /**
   * Helper function that builds a list of UI thread frames. UI thread frames are created for each matching
   * {@link UIThreadDrawType:MARSHMALLOW}
   */
  public List<AtraceFrame> getUiThreadDrivenFrames() {
    List<AtraceFrame> sliceRanges = new ArrayList<>();
    // Find all threads that match either UIThreadDrawType.
    // Note: System Tracing is only enabled on O+ devices in profiles however this matches systrace functionality for reference.
    Pattern frameRegex =
      Pattern.compile(String.format("(%s|%s)", UIThreadDrawType.MARSHMALLOW.getDrawName(), UIThreadDrawType.LEGACY.getDrawName()));

    // Loop all slices on the UI thread, any that match our regex we create a frame and perform additional processing to find
    // the end time.
    new SliceStream(myThreads.getUiThread().getSlices())
      .matchPattern(frameRegex)
      .enumerate((sliceGroup) -> {
        AtraceFrame frame = new AtraceFrame(myModel);
        double startTime = sliceGroup.getStartTime();
        double endTime = sliceGroup.getEndTime();

        // Find any frames on the render thread that overlap this slices time range.
        SliceGroup renderThreadOverlapSlice = findOverlappingDrawFrame(sliceGroup);
        if (renderThreadOverlapSlice != null) {
          double renderThreadStartTime = renderThreadOverlapSlice.getStartTime();
          double renderThreadEndTime = renderThreadOverlapSlice.getEndTime();
          // If we found one get the sync slice as a parent.
          SliceGroup renderThreadSyncSlice =
            new SliceStream(renderThreadOverlapSlice.getChildren()).matchName(RenderThreadDrawNames.THREAD_SYNC.getDrawName()).findFirst();
          // We use the THREAD_SYNC slice as our baseline for where our render thread end time is.
          endTime = Math.min(endTime, renderThreadSyncSlice.getStartTime());
          // However within that slice there may be better indicators of where our actual render thread end time is so we look for those.
          for (String name : RENDER_THREAD_SYNC_ELEMENTS) {
            renderThreadSyncSlice = new SliceStream(renderThreadOverlapSlice.getChildren()).matchName(name).findFirst();
            if (renderThreadSyncSlice != null) {
              renderThreadEndTime = renderThreadSyncSlice.getEndTime();
              break;
            }
          }
          frame.addSlice(renderThreadOverlapSlice, new Range(renderThreadStartTime, renderThreadEndTime), myThreads.getRenderThread());
        }
        frame.addSlice(sliceGroup, new Range(startTime, endTime), myThreads.getUiThread());
        sliceRanges.add(frame);
        return true;
      });
    return sliceRanges;
  }

  /**
   * Find frames that are fully render thread frames. These are frames that match {@link RenderThreadDrawNames:RENDER_THREAD_INDEP}.
   */
  private List<AtraceFrame> getRenderThreadDrivenFrames() {
    List<AtraceFrame> sliceRanges = new ArrayList<>();
    if (myThreads.getRenderThread() == null) {
      return sliceRanges;
    }
    new SliceStream(myThreads.getRenderThread().getSlices())
      .matchName(RenderThreadDrawNames.RENDER_THREAD_INDEP.getDrawName())
      .enumerate(
        (sliceGroup) -> {
          AtraceFrame frame = new AtraceFrame(myModel);
          frame.addSlice(sliceGroup, new Range(sliceGroup.getStartTime(), sliceGroup.getEndTime()),
                         myThreads.getRenderThread());
          sliceRanges.add(frame);
          return true;
        });
    return sliceRanges;
  }
}

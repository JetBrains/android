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
package com.android.tools.profilers.cpu;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profilers.DragAndDropListModel;
import com.android.tools.profilers.DragAndDropModelListElement;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuThreadsModel extends DragAndDropListModel<CpuThreadsModel.RangedCpuThread> implements Updatable {
  @NotNull private static final String RENDER_THREAD_NAME = "RenderThread";

  @NotNull private final CpuProfilerStage myStage;

  @NotNull private final Common.Session mySession;

  @NotNull private final Range myRange;

  @NotNull private final AspectObserver myAspectObserver;

  @VisibleForTesting
  protected final HashMap<Integer, RangedCpuThread> myThreadIdToCpuThread;

  public CpuThreadsModel(@NotNull Range range, @NotNull CpuProfilerStage stage, @NotNull Common.Session session) {
    myRange = range;
    myStage = stage;
    mySession = session;
    myAspectObserver = new AspectObserver();
    myThreadIdToCpuThread = new HashMap<>();

    myRange.addDependency(myAspectObserver).onChange(Range.Aspect.RANGE, this::rangeChanged);

    // Initialize first set of elements.
    rangeChanged();
    sortElements();
  }

  public void rangeChanged() {
    CpuProfiler.GetThreadsRequest.Builder request = CpuProfiler.GetThreadsRequest.newBuilder()
      .setSession(mySession)
      .setStartTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myRange.getMin()))
      .setEndTimestamp(TimeUnit.MICROSECONDS.toNanos((long)myRange.getMax()));
    CpuServiceGrpc.CpuServiceBlockingStub client = myStage.getStudioProfilers().getClient().getCpuClient();
    CpuProfiler.GetThreadsResponse response = client.getThreads(request.build());

    // Merge the two lists.
    Map<Integer, RangedCpuThread> requestedThreadsRangedCpuThreads = new HashMap<>();
    for (CpuProfiler.GetThreadsResponse.Thread newThread : response.getThreadsList()) {
      RangedCpuThread cpuThread = myThreadIdToCpuThread.computeIfAbsent(newThread.getTid(),
                                                                        id -> new RangedCpuThread(myRange, newThread.getTid(),
                                                                                                  newThread.getName()));
      requestedThreadsRangedCpuThreads.put(newThread.getTid(), cpuThread);
    }

    // Find elements that already exist and remove them from the incoming set.
    for (int i = 0; i < getSize(); i++) {
      RangedCpuThread element = getElementAt(i);
      // If our element exists in the incoming set we remove it from the set, because we do not need to do anything with it.
      // If the element does not exist it means we no longer need to show this thread and we remove it from our list of elements.
      if (requestedThreadsRangedCpuThreads.containsKey(element.getThreadId())) {
        requestedThreadsRangedCpuThreads.remove(element.getThreadId());
      }
      else {
        removeOrderedElement(element);
        i--;
      }
    }

    // Add threads that dont have an element already associated with them.
    for (RangedCpuThread element : requestedThreadsRangedCpuThreads.values()) {
      insertOrderedElement(element);
    }
  }

  private void sortElements() {
    // Grab elements before we clear them.
    Object[] elements = toArray();
    clearOrderedElements();
    // Sort the other threads by name, except for the process thread, and render thread. The process is moved to the top followed by
    // the render thread.
    Arrays.sort(elements, (a, b) -> {
      RangedCpuThread first = (RangedCpuThread)a;
      RangedCpuThread second = (RangedCpuThread)b;
      // Process main thread should be first element.
      if (first.getThreadId() == mySession.getPid()) {
        return -1;
      }
      else if (second.getThreadId() == mySession.getPid()) {
        return 1;
      }

      // Render render threads should be the next elements.
      assert first.getName() != null;
      assert second.getName() != null;
      boolean firstIsRenderThread = first.getName().equals(RENDER_THREAD_NAME);
      boolean secondIsRenderThread = second.getName().equals(RENDER_THREAD_NAME);
      if (firstIsRenderThread && secondIsRenderThread) {
        return first.getThreadId() - second.getThreadId();
      }
      else if (firstIsRenderThread) {
        return -1;
      }
      else if (secondIsRenderThread) {
        return 1;
      }

      // Finally the list is sorted by thread name, with conflicts sorted by thread id.
      int nameResult = first.getName().compareTo(second.getName());
      if (nameResult == 0) {
        return first.getThreadId() - second.getThreadId();
      }
      return nameResult;
    });

    // Even with the render thread at the top of the sorting, the pre-populated elements get priority so,
    // all of our threads will be added below our process thread in order.
    for (Object element : elements) {
      RangedCpuThread rangedCpuThread = (RangedCpuThread)element;
      insertOrderedElement(rangedCpuThread);
    }
  }

  @Override
  public void update(long elapsedNs) {
    fireContentsChanged(this, 0, size());
  }

  public class RangedCpuThread implements DragAndDropModelListElement {

    private final int myThreadId;
    private final String myName;
    private final Range myRange;
    private final StateChartModel<CpuProfilerStage.ThreadState> myModel;
    // This data series combines the sampled data series pulled from perfd, and the Atrace data series
    // populated when an atrace capture is parsed.
    private final MergeCaptureDataSeries<CpuProfilerStage.ThreadState> mySeries;
    // The Atrace data series is added to the MergeCaptureDataSeries, however it is only populated
    // when an Atrace capture is parsed. When the data series is populated the results from the
    // Atrace data series are used in place of the ThreadStateDataSeries for the range that
    // overlap.
    private final AtraceDataSeries<CpuProfilerStage.ThreadState> myAtraceDataSeries;

    public RangedCpuThread(Range range, int threadId, String name) {
      myRange = range;
      myThreadId = threadId;
      myName = name;
      myModel = new StateChartModel<>();
      ThreadStateDataSeries threadStateDataSeries = new ThreadStateDataSeries(myStage, mySession, myThreadId);
      myAtraceDataSeries = new AtraceDataSeries(myStage,
                                                (capture) -> ((AtraceCpuCapture)capture).getThreadStatesForThread(myThreadId));
      mySeries = new MergeCaptureDataSeries(myStage, threadStateDataSeries, myAtraceDataSeries);
      myModel.addSeries(new RangedSeries<>(myRange, mySeries));
    }

    public int getThreadId() {
      return myThreadId;
    }

    public String getName() {
      return myName;
    }

    public StateChartModel<CpuProfilerStage.ThreadState> getModel() {
      return myModel;
    }

    public MergeCaptureDataSeries getStateSeries() {
      return mySeries;
    }

    /**
     * @return Thread Id used to uniquely identify this object in our {@link DragAndDropListModel}
     */
    @Override
    public int getId() {
      return myThreadId;
    }
  }
}

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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.DragAndDropListModel;
import com.android.tools.adtui.model.DragAndDropModelListElement;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.systemtrace.SystemTraceCpuCapture;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuThreadsModel extends DragAndDropListModel<CpuThreadsModel.RangedCpuThread> {
  /**
   * Negative number used when no thread is selected.
   */
  public static final int NO_THREAD = -1;

  private int myThread = NO_THREAD;

  @NotNull private final StudioProfilers myProfilers;

  @NotNull private final Common.Session mySession;

  @NotNull private final Range myRange;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal")
  @NotNull private final AspectObserver myAspectObserver;

  public CpuThreadsModel(@NotNull Range range,
                         @NotNull StudioProfilers profilers,
                         @NotNull Common.Session session) {
    myRange = range;
    myProfilers = profilers;
    mySession = session;
    myAspectObserver = new AspectObserver();
    myRange.addDependency(myAspectObserver)
      .onChange(Range.Aspect.RANGE, this::nonImportRangeChanged);

    // Initialize first set of elements.
    nonImportRangeChanged();
    sortElements();
  }

  public void setThread(int thread) {
    if (myThread == thread) {
      return;
    }
    myThread = thread;
  }

  public int getThread() {
    return myThread;
  }

  private void nonImportRangeChanged() {
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)myRange.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)myRange.getMax());
    Map<Integer, RangedCpuThread> requestedThreadsRangedCpuThreads = new HashMap<>();

    GetEventGroupsResponse response = myProfilers.getClient().getTransportClient().getEventGroups(
      GetEventGroupsRequest.newBuilder()
        .setStreamId(mySession.getStreamId())
        .setPid(mySession.getPid())
        .setKind(Common.Event.Kind.CPU_THREAD)
        .setFromTimestamp(minNs)
        .setToTimestamp(maxNs)
        .build());

    // Merge the two lists.
    for (EventGroup eventGroup : response.getGroupsList()) {
      if (eventGroup.getEventsCount() > 0) {
        Cpu.CpuThreadData threadData = eventGroup.getEvents(0).getCpuThread();
        requestedThreadsRangedCpuThreads.put(threadData.getTid(), new RangedCpuThread(myRange, threadData.getTid(), threadData.getName()));
      }
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

    // Add threads that don't have an element already associated with them.
    for (RangedCpuThread element : requestedThreadsRangedCpuThreads.values()) {
      insertOrderedElement(element);
    }
    contentsChanged();
  }

  private void sortElements() {
    // Copy elements into an array before we clear them.
    RangedCpuThread[] elements = new RangedCpuThread[getSize()];
    for (int i = 0; i < getSize(); ++i) {
      elements[i] = get(i);
    }
    clearOrderedElements();

    // Sort by the ThreadInfo field.
    Arrays.sort(elements);

    // Even with the render thread at the top of the sorting, the pre-populated elements get priority so,
    // all of our threads will be added below our process thread in order.
    for (RangedCpuThread element : elements) {
      insertOrderedElement(element);
    }
  }

  private void contentsChanged() {
    fireContentsChanged(this, 0, size());
  }

  protected static ThreadState getState(Cpu.CpuThreadData.State state, boolean captured) {
    switch (state) {
      case RUNNING:
        return captured ? ThreadState.RUNNING_CAPTURED : ThreadState.RUNNING;
      case DEAD:
        return captured ? ThreadState.DEAD_CAPTURED : ThreadState.DEAD;
      case SLEEPING:
        return captured ? ThreadState.SLEEPING_CAPTURED : ThreadState.SLEEPING;
      case WAITING:
        return captured ? ThreadState.WAITING_CAPTURED : ThreadState.WAITING;
      default:
        // TODO: Use colors that have been agreed in design review.
        return ThreadState.UNKNOWN;
    }
  }

  public class RangedCpuThread implements DragAndDropModelListElement, Comparable<RangedCpuThread> {
    @NotNull private final CpuThreadInfo myThreadInfo;
    private final Range myRange;
    private final StateChartModel<ThreadState> myModel;
    /**
     * We use a {@link MergeCaptureDataSeries} that will combine the sampled {@link DataSeries} pulled from perfd, and
     * {@link SystemTraceCpuCapture}, populated when an atrace capture is parsed.
     */
    private DataSeries<ThreadState> mySeries;

    public RangedCpuThread(Range range, int threadId, String name) {
      this(range, threadId, name, null);
    }

    /**
     * When a not-null {@link CpuCapture} is passed, it means the thread is imported from a trace file. If the {@link CpuCapture} passed is
     * null, it means that we are in a profiling session. Default behavior is to obtain the {@link ThreadState} data from
     * perfd. When a capture is selected applyCapture is called and on atrace captures a {@link MergeCaptureDataSeries} is used to collect
     * data from perfd as well as the {@link SystemTraceCpuCapture}.
     */
    public RangedCpuThread(Range range, int threadId, String name, @Nullable CpuCapture capture) {
      myRange = range;
      myModel = new StateChartModel<>();
      boolean isMainThread = applyCapture(threadId, capture);
      myThreadInfo = new CpuThreadInfo(threadId, name, isMainThread);
    }

    /**
     * @return true if this thread is the main thread.
     */
    private boolean applyCapture(int threadId, @Nullable CpuCapture capture) {
      boolean isMainThread;
      mySeries = new CpuThreadStateDataSeries(myProfilers.getClient().getTransportClient(),
                                              mySession.getStreamId(),
                                              mySession.getPid(),
                                              threadId,
                                              capture);
      // If we have an Atrace capture selected then we need to create a MergeCaptureDataSeries
      if (capture != null && capture.getSystemTraceData() != null) {
        mySeries = new MergeCaptureDataSeries<>(
          capture, mySeries, new LazyDataSeries<>(() -> capture.getSystemTraceData().getThreadStatesForThread(threadId)));
      }
      // For non-imported traces, the main thread ID is equal to the process ID of the current session
      isMainThread = threadId == mySession.getPid();
      // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
      myModel.addSeries(new RangedSeries<>(myRange, mySeries));
      return isMainThread;
    }

    public int getThreadId() {
      return myThreadInfo.getId();
    }

    @NotNull
    public String getName() {
      return myThreadInfo.getName();
    }

    public StateChartModel<ThreadState> getModel() {
      return myModel;
    }

    public DataSeries<ThreadState> getStateSeries() {
      return mySeries;
    }

    /**
     * @return Thread Id used to uniquely identify this object in our {@link DragAndDropListModel}
     */
    @Override
    public int getId() {
      return getThreadId();
    }

    /**
     * See {@link CpuThreadInfo} for sort order.
     */
    @Override
    public int compareTo(@NotNull RangedCpuThread o) {
      return CpuThreadComparator.BASE.compare(myThreadInfo, o.myThreadInfo);
    }
  }
}

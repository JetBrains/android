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
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profiler.proto.CpuServiceGrpc;
import com.android.tools.profiler.proto.Transport.EventGroup;
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest;
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.atrace.AtraceCpuCapture;
import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is responsible for making an RPC call to perfd/datastore and converting the resulting proto into UI data.
 */
public class CpuThreadsModel extends DragAndDropListModel<CpuThreadsModel.RangedCpuThread> {
  @NotNull private static final String RENDER_THREAD_NAME = "RenderThread";

  @NotNull private final StudioProfilers myProfilers;

  @NotNull private final Common.Session mySession;

  @NotNull private final Range myRange;

  @NotNull private final AspectObserver myAspectObserver;

  private final boolean myIsImportedTrace;

  @VisibleForTesting
  protected final HashMap<Integer, RangedCpuThread> myThreadIdToCpuThread;

  public CpuThreadsModel(@NotNull Range range,
                         @NotNull StudioProfilers profilers,
                         @NotNull Common.Session session,
                         boolean isImportedTrace) {
    myRange = range;
    myProfilers = profilers;
    mySession = session;
    myAspectObserver = new AspectObserver();
    myThreadIdToCpuThread = new HashMap<>();
    myIsImportedTrace = isImportedTrace;
    myRange.addDependency(myAspectObserver)
      .onChange(Range.Aspect.RANGE, myIsImportedTrace ? this::importRangeChanged : this::nonImportRangeChanged);

    // Initialize first set of elements.
    nonImportRangeChanged();
    sortElements();
  }

  /**
   * In import trace mode, we always list all the capture threads, as we don't have the concept of thread states. In regular profiling,
   * if a thread is dead, it means we won't see more state changes from it at a later point, so it's OK to remove it from the list.
   * Threads in import trace mode, for example, can have 5 seconds of activity, stay inactive for 10 more seconds and have activity
   * again for other 5 seconds. As it's common for a thread to be inactive (e.g. sleeping, waiting for I/O, stopped, etc.) during its
   * lifespan, we don't change the threads list automatically to avoid a poor user experience.
   * Note that users can still explicitly change the threads order by using the drag-and-drop functionality.
   */
  private void importRangeChanged() {
    contentsChanged();
  }

  private void nonImportRangeChanged() {
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)myRange.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)myRange.getMax());
    Map<Integer, RangedCpuThread> requestedThreadsRangedCpuThreads = new HashMap<>();

    if (myProfilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()) {
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
          Common.Event first = eventGroup.getEvents(0);
          Common.Event last = eventGroup.getEvents(eventGroup.getEventsCount() - 1);
          if (last.getTimestamp() < minNs && last.getIsEnded()) {
            continue;
          }
          Cpu.CpuThreadData threadData = first.getCpuThread();
          requestedThreadsRangedCpuThreads.put(threadData.getTid(), myThreadIdToCpuThread
            .computeIfAbsent(threadData.getTid(), id -> new RangedCpuThread(myRange, threadData.getTid(), threadData.getName())));
        }
      }
    }
    else {
      CpuProfiler.GetThreadsRequest.Builder request = CpuProfiler.GetThreadsRequest.newBuilder()
        .setSession(mySession)
        .setStartTimestamp(minNs)
        .setEndTimestamp(maxNs);
      CpuServiceGrpc.CpuServiceBlockingStub client = myProfilers.getClient().getCpuClient();
      CpuProfiler.GetThreadsResponse response = client.getThreads(request.build());

      // Merge the two lists.
      for (CpuProfiler.GetThreadsResponse.Thread newThread : response.getThreadsList()) {
        RangedCpuThread cpuThread = myThreadIdToCpuThread.computeIfAbsent(newThread.getTid(),
                                                                          id -> new RangedCpuThread(myRange, newThread.getTid(),
                                                                                                    newThread.getName()));
        requestedThreadsRangedCpuThreads.put(newThread.getTid(), cpuThread);
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
    // Grab elements before we clear them.
    Object[] elements = toArray();
    clearOrderedElements();
    // Sort the other threads by name, except for the process thread, and render thread. The process is moved to the top followed by
    // the render thread.
    Arrays.sort(elements, (a, b) -> {
      RangedCpuThread first = (RangedCpuThread)a;
      RangedCpuThread second = (RangedCpuThread)b;
      // Process main thread should be first element.
      if (first.isMainThread()) {
        return -1;
      }
      else if (second.isMainThread()) {
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

  /**
   * Build a list of {@link RangedCpuThread} based from the threads contained in a given {@link CpuCapture}.
   */
  void buildImportedTraceThreads(@NotNull CpuCapture capture) {
    // Create the RangedCpuThread objects from the capture's threads
    List<RangedCpuThread> threads = capture.getThreads().stream()
      .map(thread -> new RangedCpuThread(myRange, thread.getId(), thread.getName(), capture))
      .collect(Collectors.toList());
    // Now insert the elements in order.
    threads.forEach(this::insertOrderedElement);
    sortElements();
  }

  void updateTraceThreadsForCapture(@NotNull CpuCapture capture) {
    // In the import case we do not have a thread list so we build it.
    if (myIsImportedTrace) {
      buildImportedTraceThreads(capture);
    }
    else {
      myThreadIdToCpuThread.forEach((key, value) -> {
        value.applyCapture(capture);
      });
    }
  }

  private void contentsChanged() {
    fireContentsChanged(this, 0, size());
  }

  protected static CpuProfilerStage.ThreadState getState(Cpu.CpuThreadData.State state, boolean captured) {
    switch (state) {
      case RUNNING:
        return captured ? CpuProfilerStage.ThreadState.RUNNING_CAPTURED : CpuProfilerStage.ThreadState.RUNNING;
      case DEAD:
        return captured ? CpuProfilerStage.ThreadState.DEAD_CAPTURED : CpuProfilerStage.ThreadState.DEAD;
      case SLEEPING:
        return captured ? CpuProfilerStage.ThreadState.SLEEPING_CAPTURED : CpuProfilerStage.ThreadState.SLEEPING;
      case WAITING:
        return captured ? CpuProfilerStage.ThreadState.WAITING_CAPTURED : CpuProfilerStage.ThreadState.WAITING;
      default:
        // TODO: Use colors that have been agreed in design review.
        return CpuProfilerStage.ThreadState.UNKNOWN;
    }
  }

  public class RangedCpuThread implements DragAndDropModelListElement {

    private final int myThreadId;
    private boolean myIsMainThread;
    private final String myName;
    private final Range myRange;
    private final StateChartModel<CpuProfilerStage.ThreadState> myModel;
    /**
     * If the thread is imported from a trace file (excluding an atrace one), we use a {@link ImportedTraceThreadDataSeries} to represent
     * its data. Otherwise, we use a {@link MergeCaptureDataSeries} that will combine the sampled {@link DataSeries} pulled from perfd, and
     * {@link #myAtraceDataSeries}, populated when an atrace capture is parsed.
     */
    private DataSeries<CpuProfilerStage.ThreadState> mySeries;

    public RangedCpuThread(Range range, int threadId, String name) {
      this(range, threadId, name, null);
    }

    /**
     * When a not-null {@link CpuCapture} is passed, it means the thread is imported from a trace file. If the {@link CpuCapture} passed is
     * null, it means that we are in a profiling session. Default behavior is to obtain the {@link CpuProfilerStage.ThreadState} data from
     * perfd. When a capture is selected applyCapture is called and on atrace captures a {@link MergeCaptureDataSeries} is used to collect
     * data from perfd as well as the {@link AtraceCpuCapture}.
     */
    public RangedCpuThread(Range range, int threadId, String name, @Nullable CpuCapture capture) {
      myRange = range;
      myThreadId = threadId;
      myName = name;
      myModel = new StateChartModel<>();
      applyCapture(capture);
    }

    private void applyCapture(CpuCapture capture) {
      if (myIsImportedTrace) {
        // For imported traces, the main thread ID can be obtained from the capture
        myIsMainThread = myThreadId == capture.getMainThreadId();
        if (capture.getType() == Cpu.CpuTraceType.ATRACE) {
          mySeries =
            new AtraceDataSeries<>((AtraceCpuCapture)capture, (atraceCapture) -> atraceCapture.getThreadStatesForThread(myThreadId));
        }
        else {
          // If thread is created from an imported trace (excluding atrace), we should use an ImportedTraceThreadDataSeries
          mySeries = new ImportedTraceThreadDataSeries(capture, myThreadId);
        }
      }
      else {
        mySeries = createThreadStateDataSeries(capture);
        // If we have an Atrace capture selected then we need to create a MergeCaptureDataSeries
        if (capture != null && capture.getType() == Cpu.CpuTraceType.ATRACE) {
          AtraceCpuCapture atraceCpuCapture = (AtraceCpuCapture)capture;
          AtraceDataSeries<CpuProfilerStage.ThreadState> atraceDataSeries =
            new AtraceDataSeries<>(atraceCpuCapture, (atraceCapture) -> atraceCapture.getThreadStatesForThread(myThreadId));
          mySeries = new MergeCaptureDataSeries<>(capture, mySeries, atraceDataSeries);
        }
        // For non-imported traces, the main thread ID is equal to the process ID of the current session
        myIsMainThread = myThreadId == mySession.getPid();
      }
      // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
      myModel.addSeries(new RangedSeries<>(myRange, mySeries));
    }

    private DataSeries<CpuProfilerStage.ThreadState> createThreadStateDataSeries(@Nullable CpuCapture capture) {
      return myProfilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled()
             ?
             new CpuThreadStateDataSeries(myProfilers.getClient().getTransportClient(),
                                          mySession.getStreamId(),
                                          mySession.getPid(),
                                          myThreadId,
                                          capture)
             : new LegacyCpuThreadStateDataSeries(myProfilers.getClient().getCpuClient(), mySession, myThreadId, capture);
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

    public DataSeries<CpuProfilerStage.ThreadState> getStateSeries() {
      return mySeries;
    }

    /**
     * @return Thread Id used to uniquely identify this object in our {@link DragAndDropListModel}
     */
    @Override
    public int getId() {
      return myThreadId;
    }

    public boolean isMainThread() {
      return myIsMainThread;
    }
  }
}

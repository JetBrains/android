/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.MainProcessSelector;
import com.android.tools.profilers.cpu.TraceParser;
import com.android.tools.profilers.cpu.nodemodel.AtraceNodeModel;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import trebuchet.model.CpuModel;
import trebuchet.model.CpuProcessSlice;
import trebuchet.model.Model;
import trebuchet.model.ProcessModel;
import trebuchet.model.SchedSlice;
import trebuchet.model.ThreadModel;
import trebuchet.model.base.SliceGroup;
import trebuchet.task.ImportTask;
import trebuchet.util.PrintlnImportFeedback;

/**
 * AtraceParser is a minimal implementation parsing the atrace file.
 * The class looks for the first and last lines in the file to get the total time, as well as
 * populates a minimal data structure to pass to the UI.
 * Trebuchet is our parser for atrace (systrace) raw data.
 */
public class AtraceParser implements TraceParser {
  @VisibleForTesting
  static final long UTILIZATION_BUCKET_LENGTH_US = TimeUnit.MILLISECONDS.toMicros(50);
  /**
   * A value to be used when we don't know the process we want to parse.
   * Note: The max process id values we can have is max short, and some invalid process names can be -1 so
   * to avoid confusion we use int max.
   */
  public static final int INVALID_PROCESS = Integer.MAX_VALUE;
  /**
   * The platform RenderThread is hard coded to have this name.
   */
  public static final String RENDER_THREAD_NAME = "RenderThread";

  /**
   * SurfaceFlinger is responsible for compositing all the application and system surfaces into a single buffer
   */
  private static final String SURFACE_FLINGER_PROCESS_NAME = "surfaceflinger";

  /**
   * Map of CpuThreadInfo to capture nodes. The thread info in this map does not contain process information.
   */
  private final Map<CpuThreadInfo, CaptureNode> myCaptureTreeNodes;

  /**
   * Map between processor ids and its set of {@link CpuThreadSliceInfo}.
   * Note: In kernel space all user space processes are treated at threads, where each thread has a TGID (Thread group ID).
   * The TGID is the main thread of a user space process. All references to processes outside this class refer to user space
   * processes while threads refer to threads within those processes.
   */
  private final Map<Integer, List<SeriesData<CpuThreadSliceInfo>>> myCpuSchedulingToCpuData;

  /**
   * Map between thread id, and the thread state for each state transition on that thread.
   */
  private final Map<Integer, List<SeriesData<CpuProfilerStage.ThreadState>>> myThreadStateData;

  /**
   * List of cpu utilization values for a specific process. The values range from 0 -> 100 in increments of CPU count.
   * The value needs to be a long as that is what {@link com.android.tools.adtui.model.RangedContinuousSeries} expects.
   */
  private final List<SeriesData<Long>> myCpuUtilizationSeries;

  private int myProcessId;
  /**
   * The device boot time captured at the beginning of the trace.
   */
  private double myMonoTimeAtBeginningSeconds = 0;
  private ProcessModel myProcessModel;
  // Trebuchet.Model is what Trebuchet uses to represent all captured data.
  private Model myModel;
  private Range myRange;
  private AtraceFrameManager myFrameInfo;

  @NotNull
  private final MainProcessSelector processSelector;

  /**
   * This constructor assumes we know what process we want to focus on.
   */
  public AtraceParser(String processName) {
    this(new MainProcessSelector(processName, null, null));
  }

  /**
   * This constructor assumes we know what process we want to focus on.
   */
  public AtraceParser(int processId) {
    this(new MainProcessSelector(null, processId, null));
  }

  /**
   * This constructor assumes we don't know which process we want to focus on and will use the passed {@code processSelector} to find it.
   */
  public AtraceParser(@NotNull MainProcessSelector processSelector) {
    this.processSelector = processSelector;

    myCaptureTreeNodes = new HashMap<>();
    myThreadStateData = new HashMap<>();
    myCpuSchedulingToCpuData = new HashMap<>();
    myCpuUtilizationSeries = new ArrayList<>();
  }

  @Override
  public CpuCapture parse(File file, long traceId) throws IOException {
    parseModelIfNeeded(file);
    double startTimestampUs = convertToUserTimeUs(myModel.getBeginTimestamp());
    double endTimestampUs = convertToUserTimeUs(myModel.getEndTimestamp());
    myRange = new Range(startTimestampUs, endTimestampUs);

    List<CpuThreadSliceInfo> processList = getProcessList(processSelector.getNameHint());
    if (processList.isEmpty()) {
      throw new IllegalStateException("Invalid trace without any process information.");
    }

    Integer selectedProcess = processSelector.apply(processList);
    if (selectedProcess == null) {
      throw new IllegalStateException("It was not possible to select a process for this trace.");
    }
    myProcessId = selectedProcess;

    myProcessModel = myModel.getProcesses().get(myProcessId);
    // Throw an exception instead of assert as the caller expects we will throw an exception if we failed to parse.
    if (myProcessModel == null) {
      throw new IllegalArgumentException(String.format("A process with the id %s was not found while parsing the capture.", myProcessId));
    }
    buildCaptureTreeNodes();
    buildThreadStateData();
    buildCpuStateData();
    myFrameInfo = new AtraceFrameManager(myProcessModel, convertToUserTimeUsFunction(), findRenderThreadId(myProcessModel));
    return new AtraceCpuCapture(this, myFrameInfo, traceId);
  }

  /**
   * Parses the input file and caches off the model to prevent parsing multiple times.
   */
  private void parseModelIfNeeded(@NotNull File file) throws IOException {
    if (myModel == null) {
      TrebuchetBufferProducer producer;
      if (AtraceProducer.verifyFileHasAtraceHeader(file)) {
        producer = new AtraceProducer();
      }
      else {
        producer = new PerfettoProducer();
      }
      if (!producer.parseFile(file)) {
        throw new IOException("Failed to parse file: " + file.getAbsolutePath());
      }

      ImportTask task = new ImportTask(new PrintlnImportFeedback());
      myModel = task.importBuffer(producer);
      // We check if we have a parent timestamp. If not this could be from an imported trace.
      // In the case it is 0, we use the first timestamp of our capture as a reference point.
      if (Double.compare(myModel.getParentTimestamp(), 0.0) == 0) {
        myMonoTimeAtBeginningSeconds = myModel.getBeginTimestamp();
      }
      else {
        myMonoTimeAtBeginningSeconds = myModel.getParentTimestamp() - (myModel.getParentTimestampBootTime() - myModel.getBeginTimestamp());
      }
    }
  }

  /**
   * Returns true if there is potentially missing data. While it is never
   * a guarantee if data is missing or not we make a best guess.
   * The realtime timestamp is written out as soon as we start
   * an atrace capture. If this timestamp does not exist this value
   * will be 0. The value wont exist if the user attempted to capture
   * more data than the size of the existing buffer.
   */
  public boolean isMissingData() {
    return myModel.getRealtimeTimestamp() == 0;
  }

  /**
   * An array of CPU process information is returned. This array is sorted using the following criteria,
   * 1) Process names matching the hint string.
   * 2) Processes with the most activity
   * 3) Alphabetically
   * 4) Processes without names.
   */
  @NotNull
  @VisibleForTesting
  List<CpuThreadSliceInfo> getProcessList(@Nullable String hint) {
    assert myModel != null;

    String hintLower = hint == null ? "" : hint.toLowerCase(Locale.getDefault());

    return myModel.getProcesses().values().stream().sorted((a, b) -> {
      String aNameLower = getMainThreadForProcess(a).toLowerCase(Locale.getDefault());
      String bNameLower = getMainThreadForProcess(b).toLowerCase(Locale.getDefault());

      // If either the left or right names overlap with our hint we want to bubble those elements
      // to the top.
      // Eg. Hint = "Test"
      // A = "Project"
      // B = "Test_Project"
      // The sorting should be Test_Project, Project.
      if (hintLower.contains(aNameLower) && !hintLower.contains(bNameLower)) {
        return -1;
      }
      else if (hintLower.contains(bNameLower) && !hintLower.contains(aNameLower)) {
        return 1;
      }

      // If our name starts with < then we have a process whose name did not resolve as such we bubble these elements
      // to the bottom of our list.
      // A = "<1234>"
      // B = "Test_Project"
      // The sorting should be Test_Project, <1234>
      if (aNameLower.startsWith("<") && !bNameLower.startsWith("<")) {
        return 1;
      }
      else if (bNameLower.startsWith("<") && !aNameLower.startsWith("<")) {
        return -1;
      }

      // If our project names don't match either our hint, or our <> name then we sort the elements within
      // by count of threads.
      // Note: This also applies if we have multiple projects that match our hint, or don't have a name.
      int threadsGreater = b.getThreads().size() - a.getThreads().size();
      if (threadsGreater != 0) {
        return threadsGreater;
      }

      // Finally we sort our projects by name.
      int name = aNameLower.compareTo(bNameLower);
      if (name == 0) {
        return b.getId() - a.getId();
      }
      return name;
    }).map( p -> {
      String name = getMainThreadForProcess(p);
      return new CpuThreadSliceInfo(p.getId(), name, p.getId(), name);
    }).collect(Collectors.toList());
  }

  @Override
  public boolean supportsDualClock() {
    return false;
  }

  /**
   * @param time as given to us from atrace file. Time in the atrace file is defined as
   *             systemTime(CLOCK_MONOTONIC) / 1000000000.0f returning the time in fractions of a second.
   * @return time converted to Us.
   */
  private static double secondsToUs(double time) {
    return (time * 1000000.0);
  }

  @Override
  public Map<CpuThreadInfo, CaptureNode> getCaptureTrees() {
    return myCaptureTreeNodes;
  }

  @NotNull
  public Map<Integer, List<SeriesData<CpuProfilerStage.ThreadState>>> getThreadStateDataSeries() {
    return myThreadStateData;
  }

  @NotNull
  public Map<Integer, List<SeriesData<CpuThreadSliceInfo>>> getCpuThreadSliceInfoStates() {
    return myCpuSchedulingToCpuData;
  }

  @NotNull
  public List<SeriesData<Long>> getCpuUtilizationSeries() {
    return myCpuUtilizationSeries;
  }

  public int getRenderThreadId() {
    return findRenderThreadId(myProcessModel);
  }

  /**
   * @return Returns a map of {@link CpuThreadInfo} to {@link CaptureNode}. The capture nodes are built from {@link SliceGroup} maintaining
   * the order and hierarchy.
   */
  private void buildCaptureTreeNodes() {
    Range range = getRange();
    for (ThreadModel thread : myProcessModel.getThreads()) {
      CpuThreadSliceInfo threadInfo =
        new CpuThreadSliceInfo(thread.getId(), thread.getName(), thread.getProcess().getId(), thread.getProcess().getName());
      CaptureNode root = new CaptureNode(new AtraceNodeModel(thread.getName()));
      root.setStartGlobal((long)range.getMin());
      root.setEndGlobal((long)range.getMax());
      myCaptureTreeNodes.put(threadInfo, root);
      for (SliceGroup slice : thread.getSlices()) {
        CaptureNode node = populateCaptureNode(slice, 1);
        root.addChild(node);
      }
    }
  }

  /**
   * Recursive function that builds a tree of {@link CaptureNode} from a {@link SliceGroup}
   *
   * @param slice to convert to a {@link CaptureNode}. This method will be recursively called on all children.
   * @param depth to current node. Depth starts at 0
   * @return The {@link CaptureNode} that mirrors the {@link SliceGroup} passed in.
   */
  private CaptureNode populateCaptureNode(SliceGroup slice, int depth) {
    CaptureNode node = new CaptureNode(new AtraceNodeModel(slice.getName()));
    node.setStartGlobal(convertToUserTimeUs(slice.getStartTime()));
    node.setEndGlobal(convertToUserTimeUs(slice.getEndTime()));
    node.setStartThread(convertToUserTimeUs(slice.getStartTime()));
    node.setEndThread(convertToUserTimeUs(slice.getStartTime() + slice.getCpuTime()));
    node.setDepth(depth);
    for (SliceGroup child : slice.getChildren()) {
      node.addChild(populateCaptureNode(child, depth + 1));
    }
    return node;
  }

  /**
   * Builds a map of thread id to a list of {@link CpuProfilerStage.ThreadState} series.
   */
  private void buildThreadStateData() {
    for (ThreadModel thread : myProcessModel.getThreads()) {
      List<SeriesData<CpuProfilerStage.ThreadState>> states = new ArrayList<>();
      myThreadStateData.put(thread.getId(), states);
      CpuProfilerStage.ThreadState lastState = CpuProfilerStage.ThreadState.UNKNOWN;
      for (SchedSlice slice : thread.getSchedSlices()) {
        long startTimeUs = convertToUserTimeUs(slice.getStartTime());
        CpuProfilerStage.ThreadState state = getState(slice);
        if (state != lastState) {
          states.add(new SeriesData<>(startTimeUs, state));
          lastState = state;
        }
      }
    }
  }

  /**
   * Builds a map of CPU ids to a list of {@link CpuThreadInfo} series. While building the CPU map it also builds a CPU utilization series.
   */
  private void buildCpuStateData() {
    // Add initial value to start of series for proper visualization.
    long endUserTime = convertToUserTimeUs(myModel.getEndTimestamp());
    long startUserTime = convertToUserTimeUs(myModel.getBeginTimestamp());

    for(long i = startUserTime; i < endUserTime+UTILIZATION_BUCKET_LENGTH_US; i += UTILIZATION_BUCKET_LENGTH_US) {
      myCpuUtilizationSeries.add(new SeriesData<>(i, 0L));
    }

    for (CpuModel cpu : myModel.getCpus()) {
      List<SeriesData<CpuThreadSliceInfo>> processList = new ArrayList<>();
      CpuProcessSlice lastSlice = cpu.getSlices().get(0);
      long lastBucketCounted = -1;
      for (CpuProcessSlice slice : cpu.getSlices()) {
        long sliceStartTimeUs = convertToUserTimeUs(slice.getStartTime());
        long sliceEndTimeUs = convertToUserTimeUs(slice.getEndTime());
        long durationUs = sliceEndTimeUs - sliceStartTimeUs;
        if (slice.getStartTime() > lastSlice.getEndTime()) {
          processList.add(new SeriesData<>(sliceEndTimeUs, CpuThreadSliceInfo.NULL_THREAD));
        }

        processList.add(
          new SeriesData<>(sliceStartTimeUs,
                           new CpuThreadSliceInfo(slice.getThreadId(), slice.getThreadName(), slice.getId(), slice.getName(), durationUs)));
        lastSlice = slice;

        if (slice.getId() == myProcessId) {
          // Calculate our start time.
          long startBucket = (sliceStartTimeUs - startUserTime) / UTILIZATION_BUCKET_LENGTH_US;
          // The delta between this time and the end time is how much time we still need to account for in the loop.
          long sliceTimeInBucket = sliceStartTimeUs;
          // Terminate on series bounds because the time given from the Model doesn't seem to be accurate.
          for(int i = (int)Math.max(0, startBucket); sliceEndTimeUs > sliceTimeInBucket && i < myCpuUtilizationSeries.size(); i++) {
            // We want to know the time from the start of the event to the end of the bucket so we compute where our bucket ends.
            long bucketEndTime = startUserTime + UTILIZATION_BUCKET_LENGTH_US * (i+1);
            // Because the time to the end of the bucket may (and often is) longer than our total time we take the min of the two.
            long bucketTime = Math.min(bucketEndTime, sliceEndTimeUs) - sliceTimeInBucket;
            myCpuUtilizationSeries.get(i).value += bucketTime;
            sliceTimeInBucket += bucketTime;
          }
        }
      }

      // We are done with this Cpu so we add a null process at the end to properly render this segment.
      processList.add(new SeriesData<>(convertToUserTimeUs(myModel.getEndTimestamp()), CpuThreadSliceInfo.NULL_THREAD));
      myCpuSchedulingToCpuData.put(cpu.getId(), processList);
    }

    // When we have finished processing all CPUs the utilization series contains the total time each CPU spent in each bucket.
    // Here we normalize this value across the max total wall clock time that could be spent in each bucket and end with our utilization.
    double utilizationTotalTime = UTILIZATION_BUCKET_LENGTH_US * myModel.getCpus().size();
    myCpuUtilizationSeries.replaceAll((series) -> {
      // Normalize the utilization time as a percent form 0-1 then scale up to 0-100.
      series.value = (long)(series.value/utilizationTotalTime * 100.0);
      return series;
    });
  }

  /**
   * @return converted state from the input slice.
   */
  private static CpuProfilerStage.ThreadState getState(SchedSlice slice) {
    switch (slice.getState()) {
      case RUNNING:
        return CpuProfilerStage.ThreadState.RUNNING_CAPTURED;
      case WAKING:
      case RUNNABLE:
        return CpuProfilerStage.ThreadState.RUNNABLE_CAPTURED;
      case EXIT_DEAD:
        return CpuProfilerStage.ThreadState.DEAD_CAPTURED;
      case SLEEPING:
        return CpuProfilerStage.ThreadState.SLEEPING_CAPTURED;
      case UNINTR_SLEEP:
        return CpuProfilerStage.ThreadState.WAITING_CAPTURED;
      case UNINTR_SLEEP_IO:
        return CpuProfilerStage.ThreadState.WAITING_IO_CAPTURED;
      default:
        return CpuProfilerStage.ThreadState.UNKNOWN;
    }
  }

  @Override
  public Range getRange() {
    return myRange;
  }

  private long convertToUserTimeUs(double timestampInSeconds) {
    return (long)secondsToUs((timestampInSeconds - myModel.getBeginTimestamp()) + myMonoTimeAtBeginningSeconds);
  }

  // This provides a function that implements convertToUserTimeUs, without holding into the myModel object for the begin timestamp reference.
  private Function<Double, Long> convertToUserTimeUsFunction() {
    double beginTimestamp = myModel.getBeginTimestamp();
    double monoTimeAtBeginningSeconds = myMonoTimeAtBeginningSeconds;
    return timestampInSeconds -> (long)secondsToUs((timestampInSeconds - beginTimestamp) + monoTimeAtBeginningSeconds);
  }

  /**
   * Returns the best assumed name for a process. It does this by first getting the process name.
   * If the process does not have a name it looks at each thread and if it finds one with the id
   * matching that of the process uses the name of the thread. If no main thread is found the
   * original process name is returned.
   */
  @NotNull
  private static String getMainThreadForProcess(@NotNull ProcessModel process) {
    String name = process.getName();
    if (name.startsWith("<")) {
      for (ThreadModel threads : process.getThreads()) {
        if (threads.getId() == process.getId()) {
          return threads.getName();
        }
      }
    }
    return name;
  }

  /**
   * Helper function used to find the main and render threads.
   *
   * @return ui thread model as this element is required to be non-null.
   */
  private static int findRenderThreadId(@NotNull ProcessModel process) {
    Optional<ThreadModel> renderThread =
      process.getThreads().stream().filter((thread) -> thread.getName().equalsIgnoreCase(RENDER_THREAD_NAME)).findFirst();
    return renderThread.map(ThreadModel::getId).orElse(INVALID_PROCESS);
  }

  @Nullable
  public ProcessModel getSurfaceflingerProcessModel() {
    return getProcessList(SURFACE_FLINGER_PROCESS_NAME).stream().findFirst()
      .map(threadInfo -> myModel.getProcesses().get(threadInfo.getProcessId())).orElse(null);
  }
}
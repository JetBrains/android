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
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.MainProcessSelector;
import com.android.tools.profilers.cpu.ThreadState;
import com.android.tools.profilers.cpu.TraceParser;
import com.android.tools.profilers.cpu.nodemodel.AtraceNodeModel;
import com.android.tools.profilers.systemtrace.CpuCoreModel;
import com.android.tools.profilers.systemtrace.ProcessModel;
import com.android.tools.profilers.systemtrace.SchedulingEventModel;
import com.android.tools.profilers.systemtrace.SystemTraceModelAdapter;
import com.android.tools.profilers.systemtrace.ThreadModel;
import com.android.tools.profilers.systemtrace.TraceEventModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import trebuchet.model.Model;
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
  private final Map<Integer, List<SeriesData<ThreadState>>> myThreadStateData;

  /**
   * List of cpu utilization values for a specific process. The values range from 0 -> 100 in increments of CPU count.
   * The value needs to be a long as that is what {@link com.android.tools.adtui.model.RangedContinuousSeries} expects.
   */
  private final List<SeriesData<Long>> myCpuUtilizationSeries;

  private int myProcessId;
  private ProcessModel myMainProcessModel;
  private SystemTraceModelAdapter myModelAdapter;

  @NotNull
  private final MainProcessSelector processSelector;

  // This should be always ATRACE or PERFETTO and is needed so the CpuCapture returned can contain the correct type.
  @NotNull
  private final Cpu.CpuTraceType myCpuTraceType;

  /**
   * For testing purposes, when we don't care about which process in going to be selected as the main one.
   */
  @VisibleForTesting
  public AtraceParser() {
    this(new MainProcessSelector("", 0, null));
  }

  /**
   * This constructor assumes we don't know which process we want to focus on and will use the passed {@code processSelector} to find it.
   */
  public AtraceParser(@NotNull MainProcessSelector processSelector) {
    this(Cpu.CpuTraceType.ATRACE, processSelector);
  }

  /**
   * This constructor allow us to override the CpuTraceType, for when we want to parse Perfetto traces using Trebuchet.
   * It also assumes we don't know which process we want to focus on and will use the passed {@code processSelector} to find it.
   */
  public AtraceParser(@NotNull Cpu.CpuTraceType type, @NotNull MainProcessSelector processSelector) {
    this.processSelector = processSelector;
    Preconditions.checkArgument(type == Cpu.CpuTraceType.ATRACE || type == Cpu.CpuTraceType.PERFETTO,
                                "type must be ATRACE or PERFETTO.");
    myCpuTraceType = type;

    myCaptureTreeNodes = new HashMap<>();
    myThreadStateData = new HashMap<>();
    myCpuSchedulingToCpuData = new HashMap<>();
    myCpuUtilizationSeries = new ArrayList<>();
  }

  @Override
  public CpuCapture parse(@NotNull File file, long traceId) throws IOException {
    parseModelIfNeeded(file);

    if (myModelAdapter.getProcesses().isEmpty()) {
      throw new IllegalStateException("Invalid trace without any process information.");
    }

    Integer selectedProcess = processSelector.apply(getProcessList(processSelector.getNameHint()));
    if (selectedProcess == null) {
      throw new IllegalStateException("It was not possible to select a process for this trace.");
    }
    myProcessId = selectedProcess;
    myMainProcessModel = myModelAdapter.getProcessById(myProcessId);

    // Throw an exception instead of assert as the caller expects we will throw an exception if we failed to parse.
    if (myMainProcessModel == null) {
      throw new IllegalArgumentException(String.format("A process with the id %s was not found while parsing the capture.", myProcessId));
    }
    buildCaptureTreeNodes();
    buildThreadStateData();
    buildCpuStateData();

    AtraceFrameManager frameManager = new AtraceFrameManager(myMainProcessModel);
    AtraceSurfaceflingerManager sfManager = new AtraceSurfaceflingerManager(myModelAdapter);

    Range myRange = new Range(myModelAdapter.getCaptureStartTimestampUs(), myModelAdapter.getCaptureEndTimestampUs());

    return new AtraceCpuCapture(traceId, myCpuTraceType, myRange, this, frameManager, sfManager);
  }

  /**
   * Parses the input file and caches off the model to prevent parsing multiple times.
   */
  private void parseModelIfNeeded(@NotNull File file) throws IOException {
    if (myModelAdapter == null) {
      TrebuchetBufferProducer producer;
      if (myCpuTraceType == Cpu.CpuTraceType.ATRACE) {
        producer = new AtraceProducer();
      }
      else if (myCpuTraceType == Cpu.CpuTraceType.PERFETTO){
        producer = new PerfettoProducer();
      } else {
        throw new IllegalStateException("Trying to parse something that is not ATRACE nor PERFETTO.");
      }

      if (!producer.parseFile(file)) {
        throw new IOException("Failed to parse file: " + file.getAbsolutePath());
      }

      ImportTask task = new ImportTask(new PrintlnImportFeedback());
      Model trebuchetModel = task.importBuffer(producer);
      myModelAdapter = new TrebuchetModelAdapter(trebuchetModel);
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
    return myModelAdapter.isCapturePossibleCorrupted();
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
  List<ProcessModel> getProcessList(@Nullable String hint) {
    assert myModelAdapter != null;

    String hintLower = hint == null ? "" : hint.toLowerCase(Locale.getDefault());

    return myModelAdapter.getProcesses().stream().sorted((a, b) -> {
      String aNameLower = a.getSafeProcessName().toLowerCase(Locale.getDefault());
      String bNameLower = b.getSafeProcessName().toLowerCase(Locale.getDefault());

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
    }).collect(Collectors.toList());
  }

  public Map<CpuThreadInfo, CaptureNode> getCaptureTrees() {
    return myCaptureTreeNodes;
  }

  @NotNull
  public Map<Integer, List<SeriesData<ThreadState>>> getThreadStateDataSeries() {
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

  /**
   * @return Returns a map of {@link CpuThreadInfo} to {@link CaptureNode}. The capture nodes are built from {@link TraceEventModel}
   * maintaining the order and hierarchy.
   */
  private void buildCaptureTreeNodes() {
    assert myModelAdapter != null;
    assert myMainProcessModel != null;

    for (ThreadModel thread : myMainProcessModel.getThreads()) {
      CpuThreadSliceInfo threadInfo =
        new CpuThreadSliceInfo(
          thread.getId(), thread.getName(), myMainProcessModel.getId(), myMainProcessModel.getName());
      // TODO(): Re-use instances of AtraceNodeModel for a same name.
      CaptureNode root = new CaptureNode(new AtraceNodeModel(thread.getName()));
      root.setStartGlobal(myModelAdapter.getCaptureStartTimestampUs());
      root.setEndGlobal(myModelAdapter.getCaptureEndTimestampUs());
      myCaptureTreeNodes.put(threadInfo, root);

      for (TraceEventModel event : thread.getTraceEvents()) {
        root.addChild(populateCaptureNode(event, 1));
      }
    }
  }

  /**
   * Recursive function that builds a tree of {@link CaptureNode} from a {@link TraceEventModel}
   *
   * @param traceEventModel to convert to a {@link CaptureNode}. This method will be recursively called on all children.
   * @param depth to current node. Depth starts at 0
   * @return The {@link CaptureNode} that mirrors the {@link TraceEventModel} passed in.
   */
  private CaptureNode populateCaptureNode(TraceEventModel traceEventModel, int depth) {
    CaptureNode node = new CaptureNode(new AtraceNodeModel(traceEventModel.getName()));
    node.setStartGlobal(traceEventModel.getStartTimestampUs());
    node.setEndGlobal(traceEventModel.getEndTimestampUs());
    // Should we drop these thread times, as SystemTrace does not support dual clock?
    node.setStartThread(traceEventModel.getStartTimestampUs());
    node.setEndThread(traceEventModel.getStartTimestampUs() + traceEventModel.getCpuTimeUs());
    node.setDepth(depth);

    for (TraceEventModel event : traceEventModel.getChildrenEvents()) {
      node.addChild(populateCaptureNode(event, depth + 1));
    }
    return node;
  }

  /**
   * Builds a map of thread id to a list of {@link ThreadState} series.
   */
  private void buildThreadStateData() {
    assert myModelAdapter != null;
    assert myMainProcessModel != null;

    for (ThreadModel thread : myMainProcessModel.getThreads()) {
      List<SeriesData<ThreadState>> states = new ArrayList<>();
      myThreadStateData.put(thread.getId(), states);
      ThreadState lastState = ThreadState.UNKNOWN;

      for (SchedulingEventModel sched : thread.getSchedulingEvents()) {
        if (sched.getState() != lastState) {
          states.add(new SeriesData<>(sched.getStartTimestampUs(), sched.getState()));
          lastState = sched.getState();
        }
      }
    }
  }

  /**
   * Builds a map of CPU ids to a list of {@link CpuThreadInfo} series. While building the CPU map it also builds a CPU utilization series.
   */
  private void buildCpuStateData() {
    assert myModelAdapter != null;

    long startUserTimeUs = myModelAdapter.getCaptureStartTimestampUs();
    long endUserTimeUs = myModelAdapter.getCaptureEndTimestampUs();
    for(long i = startUserTimeUs; i < endUserTimeUs+UTILIZATION_BUCKET_LENGTH_US; i += UTILIZATION_BUCKET_LENGTH_US) {
      myCpuUtilizationSeries.add(new SeriesData<>(i, 0L));
    }

    for (CpuCoreModel core : myModelAdapter.getCpuCores()) {
      List<SeriesData<CpuThreadSliceInfo>> processList = new ArrayList<>();
      long lastSliceEnd = core.getSchedulingEvents().get(0).getEndTimestampUs();

      for (SchedulingEventModel sched : core.getSchedulingEvents()) {
        long sliceStartTimeUs = sched.getStartTimestampUs();
        long sliceEndTimeUs = sched.getEndTimestampUs();

        if (sliceStartTimeUs > lastSliceEnd) {
          processList.add(new SeriesData<>(lastSliceEnd, CpuThreadSliceInfo.NULL_THREAD));
        }

        // Trebuchet data is inconsistent when accounting for the PIDs and TIDs referenced in the scheduling data from
        // each CPU (while it's ok from the per-process scheduling data).
        // Some of PIDs and TIDs are not present on the process/thread lists, so we do our best to find their data here.
        String processName= "";
        String threadName = "";
        ProcessModel process = myModelAdapter.getProcessById(sched.getProcessId());
        if (process != null) {
          processName = process.getSafeProcessName();
          ThreadModel thread = process.getThreadById().get(sched.getThreadId());
          if (thread != null) {
            threadName = thread.getName();
          }
        }

        processList.add(
          new SeriesData<>(sched.getStartTimestampUs(),
                           new CpuThreadSliceInfo(
                             sched.getThreadId(), threadName,
                             sched.getProcessId(), processName,
                             sched.getDurationUs())));
        lastSliceEnd = sliceEndTimeUs;

        if (sched.getProcessId() == myProcessId) {
          // Calculate our start time.
          long startBucket = (sliceStartTimeUs - startUserTimeUs) / UTILIZATION_BUCKET_LENGTH_US;
          // The delta between this time and the end time is how much time we still need to account for in the loop.
          long sliceTimeInBucket = sliceStartTimeUs;
          // Terminate on series bounds because the time given from the Model doesn't seem to be accurate.
          for(int i = (int)Math.max(0, startBucket); sliceEndTimeUs > sliceTimeInBucket && i < myCpuUtilizationSeries.size(); i++) {
            // We want to know the time from the start of the event to the end of the bucket so we compute where our bucket ends.
            long bucketEndTime = startUserTimeUs + UTILIZATION_BUCKET_LENGTH_US * (i+1);
            // Because the time to the end of the bucket may (and often is) longer than our total time we take the min of the two.
            long bucketTime = Math.min(bucketEndTime, sliceEndTimeUs) - sliceTimeInBucket;
            myCpuUtilizationSeries.get(i).value += bucketTime;
            sliceTimeInBucket += bucketTime;
          }
        }
      }

      // We are done with this Cpu so we add a null process at the end to properly render this segment.
      processList.add(new SeriesData<>(endUserTimeUs, CpuThreadSliceInfo.NULL_THREAD));
      myCpuSchedulingToCpuData.put(core.getId(), processList);
    }

    // When we have finished processing all CPUs the utilization series contains the total time each CPU spent in each bucket.
    // Here we normalize this value across the max total wall clock time that could be spent in each bucket and end with our utilization.
    double utilizationTotalTime = UTILIZATION_BUCKET_LENGTH_US * myModelAdapter.getCpuCores().size();
    myCpuUtilizationSeries.replaceAll((series) -> {
      // Normalize the utilization time as a percent form 0-1 then scale up to 0-100.
      series.value = (long)(series.value/utilizationTotalTime * 100.0);
      return series;
    });
  }
}
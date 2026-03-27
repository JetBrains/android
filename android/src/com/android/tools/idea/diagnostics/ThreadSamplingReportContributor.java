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
package com.android.tools.idea.diagnostics;

import static com.intellij.diagnostic.ThreadDumper.isEDT;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.diagnostics.freeze.ThreadCallTreeSorter;
import com.android.tools.idea.diagnostics.util.FrameInfo;
import com.android.tools.idea.diagnostics.util.ThreadCallTree;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.intellij.diagnostic.LogMessage;
import com.intellij.diagnostic.ThreadDump;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.diagnostic.Logger;
// TODO: android-merge; com.intellij.platform.diagnostic.plugin.freeze is not in the studio-platform jar
//import com.intellij.platform.diagnostic.plugin.freeze.FreezeReason;
//import com.intellij.platform.diagnostic.plugin.freeze.PluginFreezeWatcher;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadSamplingReportContributor implements DiagnosticReportContributor {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.diagnostics.ThreadSamplingReportContributor");
  private static final int MAX_REPORT_LENGTH_BYTES = 200_000;
  public static final int DEBUGDATA_MAX_LIST_ENTRIES = 1_000;
  private static final String NON_PLUGIN_FREEZE = "__NON_PLUGIN_FREEZE__";

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("ThreadSamplingReportContributor-%d").build());
  private final ThreadMXBean myThreadMXBean = ManagementFactory.getThreadMXBean();
  private final Object LOCK = new Object();
  private final Object DEBUGDATA_LOCK = new Object();
  @GuardedBy("mySampledStacks")
  private final List<ThreadInfo[]> mySampledStacks = new ArrayList<>();
  @GuardedBy("LOCK")
  private ScheduledFuture<?> myFutureSampling;
  @GuardedBy("LOCK")
  private ScheduledFuture<?> myFutureFinishSampling;

  private String myAwtStack = "";
  private String myReport = "";
  private String myMainFreezeReasonPluginName = null;
  private String myFreezeReasonPluginsOrdered = null;
  private String debugReport = "";

  private DiagnosticReportConfiguration myConfiguration;

  private long collectionStartTimeNs;
  private long collectionStopTimeNs;

  @GuardedBy("DEBUGDATA_LOCK")
  private final IntList samplingOffsetsMs = new IntArrayList();
  @GuardedBy("DEBUGDATA_LOCK")
  private final IntList samplingTimeMs = new IntArrayList();

  private long timeElapsedBeforeCollectionStartedMs = 0;

  @Override
  public void setup(DiagnosticReportConfiguration configuration) {
    myConfiguration = configuration;
  }

  @Override
  public void startCollection(long timeElapsedSoFarMs) {
    timeElapsedBeforeCollectionStartedMs = timeElapsedSoFarMs;
    collectionStartTimeNs = System.nanoTime();
    DiagnosticReportConfiguration configuration = myConfiguration;
    synchronized (LOCK) {
      myFutureSampling = scheduler.scheduleWithFixedDelay(
        this::sampleThreads, 0, configuration.getIntervalMs(), TimeUnit.MILLISECONDS);

      final long samplingTimeMs = configuration.getMaxSamplingTimeMs();
      if (samplingTimeMs > 0) {
        myFutureFinishSampling = scheduler.schedule(this::stop, samplingTimeMs, TimeUnit.MILLISECONDS);
      }
    }
  }

  @Override
  public void stopCollection(long totalDurationMs) {
    collectionStopTimeNs = System.nanoTime();
    stop();
    prepareReport(totalDurationMs);
  }

  @Override
  public String getReport() {
    return myReport;
  }

  @Override
  public void generateReport(BiConsumer<String, String> saveReportCallback) {
    saveReportCallback.accept("hotPathStackTrace", getAWTStack());
    saveReportCallback.accept("profileDiagnostics", getReport());
    if (myMainFreezeReasonPluginName != null) {
      saveReportCallback.accept("plugin.name", myMainFreezeReasonPluginName);
    }
    if (myFreezeReasonPluginsOrdered != null) {
      saveReportCallback.accept("plugin.names.ordered", myFreezeReasonPluginsOrdered);
    }

    if (!debugReport.isEmpty()) {
      saveReportCallback.accept("freezeReportDebugInfo", debugReport);
    }
  }

  private void stop() {
    synchronized (LOCK) {
      if (myFutureFinishSampling != null) {
        myFutureFinishSampling.cancel(false);
        myFutureFinishSampling = null;
      }
      if (myFutureSampling != null) {
        myFutureSampling.cancel(false);
        myFutureSampling = null;
      }
    }
  }

  private void sampleThreads() {
    long sampleThreadsStart = System.nanoTime();
    try {
      final ThreadInfo[] allThreads = myThreadMXBean.dumpAllThreads(false, false);
      synchronized (mySampledStacks) {
        mySampledStacks.add(allThreads);
      }
    }
    catch (Exception ignored) {
    }
    long sampleThreadsEnd = System.nanoTime();
    synchronized (DEBUGDATA_LOCK) {
      samplingOffsetsMs.add((int)TimeUnit.NANOSECONDS.toMillis(sampleThreadsStart - collectionStartTimeNs));
      samplingTimeMs.add((int)TimeUnit.NANOSECONDS.toMillis(sampleThreadsEnd - sampleThreadsStart));
    }
  }

  // TODO: android-merge; PluginFreezeWatcher and FreezeReason are not in the studio-platform jar
  //@SuppressWarnings("UnstableApiUsage")
  //@Nullable
  //private static FreezeReason computeFreezeReason(ThreadInfo[] allThreads) {
  //  final LogMessage fakeLogMessage = new LogMessage(new Throwable(), null, Collections.emptyList());
  //
  //  ThreadDumper.sort(allThreads);
  //  StringWriter writer = new StringWriter();
  //  StackTraceElement[] edtStack = dumpThreadInfos(allThreads, writer);
  //
  //  // dumpedThreads required a NotNull LogMessage, but only uses it to include it into resulting FreezeReason. We don't need it there so
  //  // it's ok to pass fake one.
  //  return PluginFreezeWatcher.getInstance().dumpedThreads(fakeLogMessage, new ThreadDump(writer.toString(), edtStack, allThreads), 0);
  //}

  /**
   * Reimplementation of {@link ThreadDumper#dumpThreadInfos} because it is a private method.
   * And the similar {@link ThreadDumper#getThreadDumpInfo(ThreadInfo[], boolean)} will do the coroutine dump as well, that we don't need in
   * this case and it will generate the unnecessary overhead.
   */
  @SuppressWarnings("UnstableApiUsage")
  private static StackTraceElement [] dumpThreadInfos(ThreadInfo @NotNull [] threadInfo, @NotNull Writer f) {
    StackTraceElement[] edtStack = null;
    for (ThreadInfo info : threadInfo) {
      if (info != null) {
        String name = info.getThreadName();
        StackTraceElement[] stackTrace = info.getStackTrace();
        if (edtStack == null && isEDT(name)) {
          edtStack = stackTrace;
        }
        ThreadDumper.dumpThreadInfo(info, f);
      }
    }
    return edtStack;
  }

  public String getAWTStack() {
    return myAwtStack;
  }

  private void prepareReport(long totalFreezeDurationMs) {
    final TruncatingStringBuilder sb = new TruncatingStringBuilder(MAX_REPORT_LENGTH_BYTES, "\n...report truncated...");
    final Map<Long, ThreadCallTree> threadMap = new HashMap<>();
    long intervalMs = myConfiguration.getIntervalMs();
    long sampleCount;
    Multiset<String> freezeReasonPluginNames = HashMultiset.create();
    synchronized (mySampledStacks) {
      sampleCount = mySampledStacks.size();
      LOG.info("Collected " + sampleCount + " samples");
      for (ThreadInfo[] sampledThreads : mySampledStacks) {
        // TODO: android-merge; FreezeReason and computeFreezeReason need PluginFreezeWatcher, which is not in the studio-platform jar
        //FreezeReason freezeReason = computeFreezeReason(sampledThreads.clone());
        //if (freezeReason == null) {
        //  freezeReasonPluginNames.add(NON_PLUGIN_FREEZE);
        //}
        //else {
        //  freezeReasonPluginNames.add(freezeReason.getPluginId().toString());
        //}
        for (ThreadInfo ti : sampledThreads) {
          ThreadCallTree callTree = threadMap.get(ti.getThreadId());
          if (callTree == null) {
            callTree = new ThreadCallTree(ti.getThreadId(), ti.getThreadName());
            threadMap.put(ti.getThreadId(), callTree);
          }
          callTree.addThreadInfo(ti, intervalMs);
        }
      }
    }

    // First, include hot path stack (for AWT thread) in the report.
    final ThreadCallTree awtThread = getAwtThread(threadMap.values());

    myAwtStack = createHotPathStackTrace(awtThread, totalFreezeDurationMs);

    final List<ThreadCallTree> sortedCallTrees = new ThreadCallTreeSorter(threadMap.values()).sort();

    for (ThreadCallTree callTree : sortedCallTrees) {
      sb.append(callTree.getReportString(myConfiguration.getFrameTimeIgnoreThresholdMs()));
      sb.append("\n"); // empty line between threads
    }

    myReport = sb.toString();

    // Find the most common plugin name across all freeze samples.
    // If the majority of samples were not caused by a plugin - don't report it.
    freezeReasonPluginNames.entrySet().stream()
      .max(Comparator.comparingInt(Multiset.Entry::getCount))
      .ifPresent(entry -> {
        if (!NON_PLUGIN_FREEZE.equals(entry.getElement())) {
          myMainFreezeReasonPluginName = entry.getElement();
        }
      });

    myFreezeReasonPluginsOrdered = Multisets.copyHighestCountFirst(freezeReasonPluginNames).entrySet().stream()
      .map(entry -> entry.getElement() + ":" + entry.getCount())
      .collect(Collectors.joining(", "));

    // Setup debug data
    long captureTimeMs = totalFreezeDurationMs - timeElapsedBeforeCollectionStartedMs;
    long timeInAwtThreadMs = awtThread != null ? awtThread.getRootFrame().getTimeSpent() : 0;

    // Add debug data for freezes with at least 5 seconds of capture time and missing at least half of samples fromh that period
    if (captureTimeMs >= 5_000 && timeInAwtThreadMs < captureTimeMs/2) {
      StringBuilder debugSb = new StringBuilder();
      debugSb.append("timeElapsedBeforeCollectionStartedMs=" + timeElapsedBeforeCollectionStartedMs +"\n");
      debugSb.append("collectionTimeMs=" + TimeUnit.NANOSECONDS.toMillis(collectionStopTimeNs - collectionStartTimeNs) +"\n");
      synchronized (DEBUGDATA_LOCK) {
        debugSb.append("sampleCount=" + sampleCount + "\n");

        // No more than first 1000 entries
        debugSb.append("samplingOffsetsMs=" + limitList(samplingOffsetsMs, DEBUGDATA_MAX_LIST_ENTRIES) + "\n");
        debugSb.append("samplingTimeMs=" + limitList(samplingTimeMs, DEBUGDATA_MAX_LIST_ENTRIES) + "\n");
      }
      debugReport = debugSb.toString();
    }
  }

  @NotNull
  private static IntList limitList(@NotNull IntList list, int limit) {
    if (list.size() < limit)
      return list;
    else
      return list.subList(0, limit);
  }

  @NotNull
  private String createHotPathStackTrace(@Nullable ThreadCallTree thread, long totalFreezeDurationMs) {
    final StringBuilder sb = new StringBuilder();
    final String threadName = thread != null ? thread.getThreadName() : "MissingAWTThread";
    sb.append("\"").append(threadName).append("\" tid=0x0 runnable\n")
      .append("     java.lang.Thread.State: RUNNABLE\n")
      .append("     Frozen for ")
      .append(TimeUnit.MILLISECONDS.toSeconds(totalFreezeDurationMs))
      .append("secs\n");

    // Special case if there was no AWT thread or it didn't collect any frames.
    FrameInfo frame = thread != null ? thread.getRootFrame() : null;
    if (frame == null) {
      sb.append("\tat ")
        .append(ThreadSamplingReportContributor.class.getName())
        .append(".missingEdtStack(Unknown source)\n");
    }

    // Collect frames to output them in reverse order
    ArrayList<FrameInfo> frames = new ArrayList<>();
    while (frame != null) {
      if (frame.getStackTraceElement() != null) {
        frames.add(frame);
      }
      frame = frame.getHottestSubframe();
      if (frame != null && frame.getTimeSpent() < myConfiguration.getFrameTimeIgnoreThresholdMs()) {
        // Don't include frames below threshold
        break;
      }
    }
    Collections.reverse(frames);

    for (FrameInfo f : frames) {
      sb.append("\tat ");
      f.appendStackTraceForCurrentFrame(sb);
      sb.append('\n');
    }
    return sb.toString();
  }

  @Nullable
  private static ThreadCallTree getAwtThread(@NotNull Collection<ThreadCallTree> threadMap) {
    return threadMap.stream().filter(ThreadCallTree::isAwtThread).findFirst().orElse(null);
  }
}

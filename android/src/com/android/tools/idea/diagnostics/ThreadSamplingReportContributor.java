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

import com.android.annotations.concurrency.GuardedBy;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.intellij.openapi.diagnostic.Logger;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ThreadSamplingReportContributor implements DiagnosticReportContributor {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.diagnostics.ThreadSamplingReportContributor");
  private static final int MAX_REPORT_LENGTH_BYTES = 200_000;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
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

  public String getAWTStack() {
    return myAwtStack;
  }

  private void prepareReport(long totalFreezeDurationMs) {
    final TruncatingStringBuilder sb = new TruncatingStringBuilder(MAX_REPORT_LENGTH_BYTES, "\n...report truncated...");
    final Map<Long, ThreadCallTree> threadMap = new HashMap<>();
    long intervalMs = myConfiguration.getIntervalMs();
    long sampleCount;
    synchronized (mySampledStacks) {
      sampleCount = mySampledStacks.size();
      LOG.info("Collected " + sampleCount + " samples");
      for (ThreadInfo[] sampledThreads : mySampledStacks) {
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

    if (awtThread != null) {
      // Put time-annotated tree for AWT thread before all other threads.
      sb.append(awtThread.getReportString(myConfiguration.getFrameTimeIgnoreThresholdMs()));
      sb.append("\n");
    }

    for (ThreadCallTree callTree : threadMap.values()) {
      if (callTree == awtThread) {
        // AWT thread has already been reported, skip it.
        continue;
      }
      sb.append(callTree.getReportString(myConfiguration.getFrameTimeIgnoreThresholdMs()));
      sb.append("\n"); // empty line between threads
    }

    myReport = sb.toString();

    // Setup debug data
    long captureTimeMs = totalFreezeDurationMs - timeElapsedBeforeCollectionStartedMs;
    long timeInAwtThreadMs = awtThread != null ? awtThread.myRootFrame.myTimeSpent : 0;

    // Add debug data for freezes with at least 5 seconds of capture time and missing at least half of samples fromh that period
    if (captureTimeMs >= 5_000 && timeInAwtThreadMs < captureTimeMs/2) {
      StringBuilder debugSb = new StringBuilder();
      debugSb.append("timeElapsedBeforeCollectionStartedMs=" + timeElapsedBeforeCollectionStartedMs +"\n");
      debugSb.append("collectionTimeMs=" + TimeUnit.NANOSECONDS.toMillis(collectionStopTimeNs - collectionStartTimeNs) +"\n");
      synchronized (DEBUGDATA_LOCK) {
        debugSb.append("sampleCount=" + sampleCount + "\n");

        // No more than first 1000 entries
        debugSb.append("samplingOffsetsMs=" + samplingOffsetsMs.intStream().limit(1000) + "\n");
        debugSb.append("samplingTimeMs=" + samplingOffsetsMs.intStream().limit(1000) + "\n");
      }
      debugReport = debugSb.toString();
    }
  }

  @NotNull
  private String createHotPathStackTrace(@Nullable ThreadCallTree thread, long totalFreezeDurationMs) {
    final StringBuilder sb = new StringBuilder();
    final String threadName = thread != null ? thread.myThreadName : "MissingAWTThread";
    sb.append("\"").append(threadName).append("\" tid=0x0 runnable\n")
      .append("     java.lang.Thread.State: RUNNABLE\n")
      .append("     Frozen for ")
      .append(TimeUnit.MILLISECONDS.toSeconds(totalFreezeDurationMs))
      .append("secs\n");

    // Special case if there was no AWT thread or it didn't collect any frames.
    FrameInfo frame = thread != null ? thread.myRootFrame : null;
    if (frame == null) {
      sb.append("\tat ")
        .append(ThreadSamplingReportContributor.class.getName())
        .append(".missingEdtStack(Unknown source)\n");
    }

    // Collect frames to output them in reverse order
    ArrayList<FrameInfo> frames = new ArrayList<>();
    while (frame != null) {
      if (frame.myStackTraceElement != null) {
        frames.add(frame);
      }
      frame = frame.getHottestSubframe();
      if (frame != null && frame.myTimeSpent < myConfiguration.getFrameTimeIgnoreThresholdMs()) {
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
    return threadMap.stream().filter(t -> t.myThreadName.startsWith("AWT-EventQueue-")).findFirst().orElse(null);
  }

  private static class ThreadCallTree {
    private long myThreadId;
    private final String myThreadName;
    private final FrameInfo myRootFrame;

    public ThreadCallTree(long threadId, String threadName) {
      myThreadId = threadId;
      myThreadName = threadName;
      myRootFrame = new FrameInfo(null);
    }

    public void addThreadInfo(ThreadInfo ti, long timeSpent) {
      myRootFrame.addThreadInfo(ti, timeSpent);
    }

    public String getReportString(long frameTimeIgnoreThresholdMs) {
      return myThreadName + ", TID: " + myThreadId + myRootFrame.getReportString(frameTimeIgnoreThresholdMs);
    }
  }

  private static class FrameInfo {
    public static final FrameInfo[] EMPTY_FRAME_INFOS = {};
    private static final String INDENT_STRING = "  ";
    private static final String INDENT_MARK_STRING = "+ ";
    private static final String[][] ourIdleApplicationImplThread = new String[][]{
      {
        "java.lang.Thread.run",
        "java.util.concurrent.ThreadPoolExecutor$Worker.run",
        "java.util.concurrent.ThreadPoolExecutor.runWorker",
        "java.util.concurrent.ThreadPoolExecutor.getTask",
        "java.util.concurrent.SynchronousQueue.poll",
        "java.util.concurrent.SynchronousQueue$TransferStack.transfer",
        "java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill",
        "java.util.concurrent.locks.LockSupport.parkNanos",
        "sun.misc.Unsafe.park"
      }, {
      "java.util.concurrent.ForkJoinWorkerThread.run",
      "java.util.concurrent.ForkJoinPool.runWorker",
      "java.util.concurrent.ForkJoinPool.awaitWork",
      "sun.misc.Unsafe.park"
    }, {
      "java.lang.Thread.run",
      "java.util.concurrent.ThreadPoolExecutor$Worker.run",
      "java.util.concurrent.ThreadPoolExecutor.runWorker",
      "java.util.concurrent.ThreadPoolExecutor.getTask",
      "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take",
      "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take",
      "java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await",
      "java.util.concurrent.locks.LockSupport.park",
      "sun.misc.Unsafe.park"
    }
    };
    private static final boolean INCLUDE_SOURCE_INFO_IN_REPORT = true;

    private static final Comparator<StackTraceElement> STACK_TRACE_ELEMENT_COMPARATOR_NO_SOURCE =
      (a, b) ->
        ComparisonChain.start().
          compare(a.getClassName(), b.getClassName(), Ordering.natural().nullsFirst()).
                         compare(a.getMethodName(), b.getMethodName(), Ordering.natural().nullsFirst()).
                         result();

    private static final Comparator<StackTraceElement> STACK_TRACE_ELEMENT_COMPARATOR_WITH_SOURCE =
      (a, b) ->
        ComparisonChain.start().
          compare(a.getClassName(), b.getClassName(), Ordering.natural().nullsFirst()).
                         compare(a.getMethodName(), b.getMethodName(), Ordering.natural().nullsFirst()).
                         compare(a.getFileName(), b.getFileName(), Ordering.natural().nullsFirst()).
                         compare(a.getLineNumber(), b.getLineNumber(), Ordering.natural().nullsFirst()).
                         result();

    private static final Comparator<StackTraceElement> STACK_TRACE_ELEMENT_COMPARATOR =
      INCLUDE_SOURCE_INFO_IN_REPORT ? STACK_TRACE_ELEMENT_COMPARATOR_WITH_SOURCE : STACK_TRACE_ELEMENT_COMPARATOR_NO_SOURCE;

    private static final Comparator<FrameInfo> FRAME_INFO_COMPARATOR =
      (a, b) ->
        ComparisonChain.start().
          compare(b.myTimeSpent, a.myTimeSpent).
                         compare(a.myStackTraceElement, b.myStackTraceElement, STACK_TRACE_ELEMENT_COMPARATOR).
                         result();

    private final StackTraceElement myStackTraceElement;
    private final SortedMap<StackTraceElement, FrameInfo> myChildren;
    private long myTimeSpent;

    private FrameInfo(@Nullable StackTraceElement element) {
      myChildren = new TreeMap<>(STACK_TRACE_ELEMENT_COMPARATOR);
      myStackTraceElement = element;
    }

    @Nullable
    public FrameInfo getHottestSubframe() {
      FrameInfo result = null;
      for (FrameInfo fi : myChildren.values()) {
        if (result == null || fi.myTimeSpent > result.myTimeSpent) {
          result = fi;
        }
      }
      return result;
    }

    public void addThreadInfo(@NotNull ThreadInfo ti, long timeSpent) {
      if (isIdleThread(ti)) {
        return;
      }
      addStack(ti.getStackTrace(), timeSpent);
    }

    private void addStack(@NotNull StackTraceElement[] stackTrace, long timeSpent) {
      FrameInfo currentFrameInfo = this;
      int index = stackTrace.length - 1;
      while (index >= 0) {
        currentFrameInfo.myTimeSpent += timeSpent;
        StackTraceElement element = stackTrace[index];
        FrameInfo fi = currentFrameInfo.myChildren.get(element);
        if (fi == null) {
          fi = new FrameInfo(element);
          currentFrameInfo.myChildren.put(element, fi);
        }
        currentFrameInfo = fi;
        index--;
      }
      currentFrameInfo.myTimeSpent += timeSpent;
    }

    public String getReportString(long frameTimeIgnoreThreshold) {
      StringBuilder indentString = new StringBuilder();
      return getReportStringWithIndent(frameTimeIgnoreThreshold, indentString, false);
    }

    @NotNull
    private String getReportStringWithIndent(long frameTimeIgnoreThreshold, @NotNull StringBuilder indentString, boolean addMark) {
      StringBuilder sb = new StringBuilder();
      if (addMark) {
        sb.append(indentString.subSequence(0, indentString.length() - INDENT_MARK_STRING.length()));
        sb.append(INDENT_MARK_STRING);
      }
      else {
        sb.append(indentString);
      }
      appendStackTraceForCurrentFrame(sb);
      sb.append(" [").append(myTimeSpent).append("ms]");
      sb.append('\n');

      FrameInfo[] childFrames = myChildren.values().toArray(EMPTY_FRAME_INFOS);
      Arrays.sort(childFrames, FRAME_INFO_COMPARATOR);

      boolean shouldIndent = Arrays.stream(childFrames).filter(fi -> fi.myTimeSpent > frameTimeIgnoreThreshold).count() > 1;
      if (shouldIndent) indentString.append(INDENT_STRING);
      for (FrameInfo fi : childFrames) {
        if (fi.myTimeSpent > frameTimeIgnoreThreshold) {
          sb.append(fi.getReportStringWithIndent(frameTimeIgnoreThreshold, indentString, shouldIndent));
        }
      }

      if (shouldIndent) indentString.delete(indentString.length() - INDENT_STRING.length(), indentString.length());
      return sb.toString();
    }

    private void appendStackTraceForCurrentFrame(@NotNull StringBuilder sb) {
      if (myStackTraceElement != null) {
        if (INCLUDE_SOURCE_INFO_IN_REPORT) {
          sb.append(myStackTraceElement.toString());
        }
        else {
          sb.append(myStackTraceElement.getClassName());
          sb.append(".");
          sb.append(myStackTraceElement.getMethodName());
          sb.append(myStackTraceElement.isNativeMethod() ? "(Native Method)" : "");
        }
      }
    }

    private static boolean isIdleThread(ThreadInfo ti) {
      StackTraceElement[] stackTraceElements = ti.getStackTrace();
      for (String[] templateIdleStack : ourIdleApplicationImplThread) {
        if (stackTraceElements.length == templateIdleStack.length) {
          int i;
          for (i = 0; i < templateIdleStack.length; i++) {
            StackTraceElement stackTraceElement = stackTraceElements[stackTraceElements.length - i - 1];
            if (!templateIdleStack[i].equals(stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName())) {
              break;
            }
          }
          if (i == templateIdleStack.length) {
            return true;
          }
        }
      }
      return false;
    }
  }
}

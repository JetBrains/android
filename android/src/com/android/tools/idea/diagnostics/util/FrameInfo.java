/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.util;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import java.lang.management.ThreadInfo;
import java.util.*;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FrameInfo {
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
      "java.util.concurrent.locks.LockSupport.parkNanos",
      "jdk.internal.misc.Unsafe.park",
    }, {
    "java.util.concurrent.ForkJoinWorkerThread.run",
    "java.util.concurrent.ForkJoinPool.runWorker",
    "java.util.concurrent.ForkJoinPool.awaitWork",
    "java.util.concurrent.locks.LockSupport.park",
    "jdk.internal.misc.Unsafe.park"
  }, {
    "java.util.concurrent.ForkJoinWorkerThread.run",
    "java.util.concurrent.ForkJoinPool.runWorker",
    "java.util.concurrent.ForkJoinPool.awaitWork",
    "java.util.concurrent.locks.LockSupport.parkUntil",
    "jdk.internal.misc.Unsafe.park",
  }, {
    "java.lang.Thread.run",
    "java.util.concurrent.ThreadPoolExecutor$Worker.run",
    "java.util.concurrent.ThreadPoolExecutor.runWorker",
    "java.util.concurrent.ThreadPoolExecutor.getTask",
    "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take",
    "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take",
    "java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos",
    "java.util.concurrent.locks.LockSupport.parkNanos",
    "jdk.internal.misc.Unsafe.park"
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
  }, {
    "java.lang.Thread.run",
    "java.util.concurrent.Executors$PrivilegedThreadFactory$1.run",
    "java.security.AccessController.doPrivileged",
    "java.security.AccessController.executePrivileged",
    "java.util.concurrent.Executors$PrivilegedThreadFactory$1$1.run",
    "java.util.concurrent.Executors$PrivilegedThreadFactory$1$1.run",
    "java.util.concurrent.ThreadPoolExecutor$Worker.run",
    "java.util.concurrent.ThreadPoolExecutor.runWorker",
    "java.util.concurrent.ThreadPoolExecutor.getTask",
    "java.util.concurrent.SynchronousQueue.poll",
    "java.util.concurrent.SynchronousQueue$TransferStack.transfer",
    "java.util.concurrent.locks.LockSupport.parkNanos",
    "jdk.internal.misc.Unsafe.park"
  }, {
    "kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run",
    "kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker",
    "kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.tryPark",
    "kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.park",
    "java.util.concurrent.locks.LockSupport.parkNanos",
    "jdk.internal.misc.Unsafe.park"
  }, {
    "kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run",
    "kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker",
    "java.util.concurrent.locks.LockSupport.parkNanos",
    "jdk.internal.misc.Unsafe.park"
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

  public StackTraceElement getStackTraceElement() {
    return myStackTraceElement;
  }

  private final StackTraceElement myStackTraceElement;
  private final SortedMap<StackTraceElement, FrameInfo> myChildren;

  public long getTimeSpent() {
    return myTimeSpent;
  }

  private long myTimeSpent;
  @Nullable
  private List<String> leafInfo = null;

  FrameInfo(@Nullable StackTraceElement element) {
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
    addThreadInfo(ti, timeSpent, null);
  }

  public void addThreadInfo(@NotNull ThreadInfo ti, long timeSpent, @Nullable final String leafInfo) {
    if (isIdleThread(ti)) {
      return;
    }
    addStack(ti.getStackTrace(), timeSpent, leafInfo);
  }

  public void addStack(@NotNull StackTraceElement[] stackTrace, long timeSpent, @Nullable final String leafInfo) {
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
    if (leafInfo != null) {
      if (currentFrameInfo.leafInfo == null) {
        currentFrameInfo.leafInfo = Lists.newArrayList();
      }
      currentFrameInfo.leafInfo.add(leafInfo);
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
    if (leafInfo != null) {
      sb.append(indentString).append("{").append(String.join(";", leafInfo)).append("}").append('\n');
    }

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

  public void appendStackTraceForCurrentFrame(@NotNull StringBuilder sb) {
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

  boolean exists(Predicate<StackTraceElement> predicate) {
    Queue<FrameInfo> queue = new ArrayDeque<>();
    queue.add(this);
    while (!queue.isEmpty()) {
      FrameInfo info = queue.remove();
      queue.addAll(info.myChildren.values());
      StackTraceElement stackTraceElement = info.myStackTraceElement;
      if (stackTraceElement != null && predicate.test(stackTraceElement)) {
        return true;
      }
    }
    return false;
  }

  int computeMaxDepth() {
    int depth = 0;
    Queue<FrameInfo> queue = new ArrayDeque<>();
    Queue<FrameInfo> queueNextLevel = new ArrayDeque<>();
    queue.add(this);
    while (!queue.isEmpty()) {
      depth++;
      while (!queue.isEmpty()) {
        FrameInfo info = queue.remove();
        queueNextLevel.addAll(info.myChildren.values());
      }
      Queue<FrameInfo> tmp = queue;
      queue = queueNextLevel;
      queueNextLevel = tmp;
    }
    return depth;
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
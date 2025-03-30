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

import java.lang.management.ThreadInfo;

import org.jetbrains.annotations.NotNull;
import java.util.function.Predicate;

public class ThreadCallTree {
  private long myThreadId;
  private final String myThreadName;

  public String getThreadName() {
    return myThreadName;
  }

  @NotNull
  public FrameInfo getRootFrame() {
    return myRootFrame;
  }

  @NotNull
  private final FrameInfo myRootFrame;

  public ThreadCallTree(long threadId, String threadName) {
    myThreadId = threadId;
    myThreadName = threadName;
    myRootFrame = new FrameInfo(null);
  }

  public void addThreadInfo(ThreadInfo ti, long timeSpent) {
    myRootFrame.addThreadInfo(ti, timeSpent);
  }

  public void addThreadInfoWithLabels(ThreadInfo ti, long timeSpent, @NotNull final String leafInfo) {
    myRootFrame.addThreadInfo(ti, timeSpent, leafInfo);
  }

  public String getReportString(long frameTimeIgnoreThresholdMs) {
    return myThreadName + ", TID: " + myThreadId + myRootFrame.getReportString(frameTimeIgnoreThresholdMs);
  }

  public int computeMaxDepth() {
    return myRootFrame.computeMaxDepth();
  }

  public boolean isAwtThread() {
    return myThreadName.startsWith("AWT-EventQueue-");
  }

  public boolean exists(@NotNull Predicate<StackTraceElement> predicate) {
    return myRootFrame.exists(predicate);
  }
}
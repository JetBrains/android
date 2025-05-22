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
package com.android.tools.swingp;

import com.android.tools.swingp.json.IncludeMethodsSerializer;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Root of every call tree. As in, all {@link MethodStat} call trees are rooted at a {@link ThreadStat}.
 */
@JsonAdapter(IncludeMethodsSerializer.class)
public class ThreadStat {
  @NotNull private final SoftReference<Thread> myThread;
  @NotNull private final LinkedBlockingQueue<MethodStat> myRootStats;
  @NotNull private Stack<MethodStat> myMethodStack = new Stack<>();

  private boolean myIsRecording;

  @SerializedName("threadId")
  private final long myThreadId;
  @SerializedName("threadName")
  @NotNull private final String myThreadName;

  @SerializedName("classType")
  private String getClassType() {
    return getClass().getSimpleName();
  }

  @SerializedName("events")
  private List<MethodStat> getEvents() {
    List<MethodStat> methodStatList = new ArrayList<>();
    myRootStats.drainTo(methodStatList);
    return methodStatList;
  }

  ThreadStat() {
    this(Thread.currentThread());
  }

  @VisibleForTesting
  public ThreadStat(@NotNull Thread thread) {
    myThread = new SoftReference<>(thread);
    myThreadId = thread.getId();
    myThreadName = thread.getName();
    myRootStats = new LinkedBlockingQueue<>();
  }

  @Nullable
  public Thread getThread() {
    return myThread.get();
  }

  public void pushMethod(@NotNull MethodStat currentStat) {
    // If recording is stopped, let stats go through until the stack is popped.
    if (myMethodStack.empty() && !myIsRecording) {
      return;
    }

    if (!myMethodStack.empty()) {
      myMethodStack.peek().addChildStat(currentStat);
    }
    myMethodStack.push(currentStat);
  }

  public void popMethod(@NotNull MethodStat verification) {
    // If recording is stopped, let stats go through until the stack is popped.
    if (myMethodStack.empty() && !myIsRecording) {
      return;
    }

    if (myMethodStack.empty()) {
      throw new RuntimeException("MethodStat#endMethod called more than once");
    }

    MethodStat methodStat = myMethodStack.pop();
    if (methodStat != verification) {
      throw new RuntimeException("Did not call MethodStat#endMethod at the end of the method or it was called more than once");
    }

    if (myMethodStack.empty()) {
      // Add only the complete call tree at the end, so we don't get incomplete stacks during dumps.
      myRootStats.add(methodStat);
    }
  }

  @NotNull
  public ThreadStat setIsRecording(boolean recording) {
    myIsRecording = recording;
    return this;
  }
}

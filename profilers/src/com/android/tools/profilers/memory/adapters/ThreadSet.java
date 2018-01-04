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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.profilers.stacktrace.ThreadId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This classifier classifies {@link InstanceObject}s based on its allocation's thread ID. If no allocations are available, the instances
 * are classified using a {@link MethodSet.MethodClassifier} (which will further classify under {@link ClassSet.ClassClassifier} if no stack
 * information is available).
 */
public class ThreadSet extends ClassifierSet {
  @NotNull private final CaptureObject myCaptureObject;
  @NotNull private final ThreadId myThreadId;

  @NotNull
  public static Classifier createDefaultClassifier(@NotNull CaptureObject captureObject) {
    return new ThreadClassifier(captureObject);
  }

  public ThreadSet(@NotNull CaptureObject captureObject, @NotNull ThreadId threadId) {
    super(threadId.toString());
    myCaptureObject = captureObject;
    myThreadId = threadId;
  }

  @NotNull
  public ThreadId getThreadId() {
    return myThreadId;
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    return MethodSet.createDefaultClassifier(myCaptureObject);
  }

  private static final class ThreadClassifier extends Classifier {
    @NotNull private final CaptureObject myCaptureObject;
    @NotNull private final Map<ThreadId, ThreadSet> myThreadSets = new LinkedHashMap<>();
    @NotNull private final Classifier myMethodSetClassifier;

    private ThreadClassifier(@NotNull CaptureObject captureObject) {
      myCaptureObject = captureObject;
      myMethodSetClassifier = MethodSet.createDefaultClassifier(myCaptureObject);
    }

    @Nullable
    @Override
    public ClassifierSet getClassifierSet(@NotNull InstanceObject instance, boolean createIfAbsent) {
      if (instance.getAllocationThreadId() != ThreadId.INVALID_THREAD_ID) {
        ThreadId threadId = instance.getAllocationThreadId();
        ThreadSet threadSet = myThreadSets.get(threadId);
        if (threadSet == null && createIfAbsent) {
          threadSet = new ThreadSet(myCaptureObject, threadId);
          myThreadSets.put(threadId, threadSet);
        }
        return threadSet;
      }
      else {
        return myMethodSetClassifier.getClassifierSet(instance, createIfAbsent);
      }
    }

    @NotNull
    @Override
    public List<ClassifierSet> getFilteredClassifierSets() {
      return Stream.concat(myThreadSets.values().stream(), myMethodSetClassifier.getFilteredClassifierSets().stream())
        .filter(child -> !child.getIsFiltered()).collect(Collectors.toList());
    }

    @NotNull
    @Override
    protected List<ClassifierSet> getAllClassifierSets() {
      return Stream.concat(myThreadSets.values().stream(), myMethodSetClassifier.getAllClassifierSets().stream())
        .collect(Collectors.toList());
    }
  }
}

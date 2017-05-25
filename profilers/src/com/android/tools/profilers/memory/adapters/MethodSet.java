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

import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profilers.stacktrace.CodeLocation;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classifies {@link InstanceObject}s based on a particular stack trace line of its allocation stack. If the end of the stack is reached or
 * if there's no stack, then the instances are classified under {@link ClassSet.ClassClassifier}s.
 */
public class MethodSet extends ClassifierSet {
  @NotNull private final CodeLocation myCodeLocation;
  @NotNull private final CaptureObject myCaptureObject;
  private final int myCallstackDepth;

  @NotNull
  public static Classifier createDefaultClassifier(@NotNull CaptureObject captureObject) {
    return new MethodClassifier(captureObject, 0);
  }

  public MethodSet(@NotNull CaptureObject captureObject, @NotNull CodeLocation codeLocation, int callstackDepth) {
    super(convertCodeLocationToString(codeLocation));
    myCaptureObject = captureObject;
    myCallstackDepth = callstackDepth;
    myCodeLocation = codeLocation;
  }

  @NotNull
  public CodeLocation getCodeLocation() {
    return myCodeLocation;
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    return new MethodClassifier(myCaptureObject, myCallstackDepth);
  }

  private static String convertCodeLocationToString(@NotNull CodeLocation codeLocation) {
    String name = codeLocation.getMethodName();
    int lineNumber = codeLocation.getLineNumber();
    String className = codeLocation.getClassName();
    StringBuilder builder = new StringBuilder();

    if (name != null) {
      builder.append(name).append("()");
      if (lineNumber != CodeLocation.INVALID_LINE_NUMBER) {
        builder.append(":").append(lineNumber);
      }
    }
    else {
      builder.append("<unknown method>");
    }
    builder.append(" (").append(className).append(")");
    return builder.toString();
  }

  private static final class MethodClassifier extends Classifier {
    @NotNull private final CaptureObject myCaptureObject;
    @NotNull private final Map<CodeLocation, MethodSet> myStackLineMap = new HashMap<>();
    @NotNull private final Map<ClassDb.ClassEntry, ClassSet> myClassMap = new HashMap<>();
    private final int myDepth;

    private MethodClassifier(@NotNull CaptureObject captureObject, int depth) {
      myCaptureObject = captureObject;
      myDepth = depth;
    }

    @NotNull
    @Override
    public ClassifierSet getOrCreateClassifierSet(@NotNull InstanceObject instance) {
      AllocationStack allocationStack = instance.getCallStack();
      if (allocationStack != null && allocationStack.getStackFramesCount() > 0 && myDepth < allocationStack.getStackFramesCount()) {
        CodeLocation codeLocation = AllocationStackConverter.getCodeLocation(allocationStack.getStackFrames(allocationStack.getStackFramesCount() - myDepth - 1));
        return myStackLineMap.computeIfAbsent(codeLocation, cl -> new MethodSet(myCaptureObject, cl, myDepth + 1));
      }
      else {
        return myClassMap.computeIfAbsent(instance.getClassEntry(), ClassSet::new);
      }
    }

    @NotNull
    @Override
    public List<ClassifierSet> getClassifierSets() {
      return Stream.concat(myStackLineMap.values().stream(), myClassMap.values().stream()).collect(Collectors.toList());
    }
  }
}

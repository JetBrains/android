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
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoRequest;
import com.android.tools.profiler.proto.MemoryProfiler.StackFrameInfoResponse;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classifies {@link InstanceObject}s based on a particular stack trace line of its allocation stack. If the end of the stack is reached or
 * if there's no stack, then the instances are classified under {@link ClassSet.ClassClassifier}s.
 */
public class MethodSet extends ClassifierSet {
  @NotNull private final MethodSetInfo myMethodInfo;
  @NotNull private final CaptureObject myCaptureObject;
  private final int myCallstackDepth;

  @NotNull
  public static Classifier createDefaultClassifier(@NotNull CaptureObject captureObject) {
    return new MethodClassifier(captureObject, 0);
  }

  public MethodSet(@NotNull CaptureObject captureObject, @NotNull MethodSetInfo methodInfo, int callstackDepth) {
    super(() -> methodInfo.getName());
    myCaptureObject = captureObject;
    myMethodInfo = methodInfo;
    myCallstackDepth = callstackDepth;
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    return new MethodClassifier(myCaptureObject, myCallstackDepth);
  }

  @NotNull
  public String getClassName() {
    return myMethodInfo.getClassName();
  }

  @NotNull
  public String getMethodName() {
    return myMethodInfo.getMethodName();
  }

  private static final class MethodClassifier extends Classifier {
    @NotNull private final CaptureObject myCaptureObject;
    @NotNull private final Map<MethodSetInfo, MethodSet> myStackLineMap = new LinkedHashMap<>();
    @NotNull private final Map<ClassDb.ClassEntry, ClassSet> myClassMap = new LinkedHashMap<>();
    private final int myDepth;

    private MethodClassifier(@NotNull CaptureObject captureObject, int depth) {
      myCaptureObject = captureObject;
      myDepth = depth;
    }

    @NotNull
    @Override
    public ClassifierSet getOrCreateClassifierSet(@NotNull InstanceObject instance) {
      AllocationStack stack = instance.getCallStack();
      int stackDepth = instance.getCallStackDepth();
      if (stack != null && stackDepth > 0 && myDepth < stackDepth) {
        switch (stack.getFrameCase()) {
          case FULL_STACK:
            AllocationStack.StackFrameWrapper fullStack = stack.getFullStack();
            AllocationStack.StackFrame stackFrame = fullStack.getFrames(fullStack.getFramesCount() - myDepth - 1);
            MethodSetInfo fullMethodInfo = new MethodSetInfo(myCaptureObject, stackFrame.getClassName(), stackFrame.getMethodName());
            return myStackLineMap.computeIfAbsent(fullMethodInfo, info -> new MethodSet(myCaptureObject, info, myDepth + 1));
          case SMALL_STACK:
            AllocationStack.SmallFrameWrapper smallStack = stack.getSmallStack();
            AllocationStack.SmallFrame smallFrame = smallStack.getFrames(smallStack.getFramesCount() - myDepth - 1);
            MethodSetInfo lazyMethodInfo = new MethodSetInfo(myCaptureObject, smallFrame.getMethodId());
            return myStackLineMap.computeIfAbsent(lazyMethodInfo, info -> new MethodSet(myCaptureObject, info, myDepth + 1));
          default:
            break;
        }
      }
      return myClassMap.computeIfAbsent(instance.getClassEntry(), ClassSet::new);
    }

    @NotNull
    @Override
    public List<ClassifierSet> getClassifierSets() {
      return Stream.concat(myStackLineMap.values().stream(), myClassMap.values().stream()).filter(child -> !child.isEmpty())
        .collect(Collectors.toList());
    }
  }

  private static final class MethodSetInfo {
    static final long INVALID_METHOD_ID = -1;

    @NotNull private final CaptureObject myCaptureObject;

    private long myMethodId;
    @Nullable private String myClassName;
    @Nullable private String myMethodName;

    private boolean myResolvedNames;
    private int myHashCode;

    MethodSetInfo(@NotNull CaptureObject captureObject, @NotNull String className, @NotNull String methodName) {
      myCaptureObject = captureObject;
      myClassName = className;
      myMethodName = methodName;
      myMethodId = INVALID_METHOD_ID;
      myHashCode = Arrays.hashCode(new int[]{myClassName.hashCode(), myMethodName.hashCode()});
      myResolvedNames = true;
    }

    MethodSetInfo(@NotNull CaptureObject captureObject, long methodId) {
      myCaptureObject = captureObject;
      myMethodId = methodId;
      myHashCode = Long.hashCode(myMethodId);
      myResolvedNames = false;
    }

    @Override
    public int hashCode() {
      return myHashCode;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MethodSetInfo)) {
        return false;
      }

      MethodSetInfo other = (MethodSetInfo)obj;
      if (myMethodId == INVALID_METHOD_ID) {
        return StringUtil.equals(myClassName, other.myClassName) && StringUtil.equals(myMethodName, other.myMethodName);
      }
      else {
        return myMethodId == other.myMethodId;
      }
    }

    @NotNull
    String getName() {
      resolveNames();

      StringBuilder builder = new StringBuilder();
      if (myMethodName != null) {
        builder.append(myMethodName).append("()");
      }
      else {
        builder.append("<unknown method>");
      }
      builder.append(" (").append(myClassName).append(")");
      return builder.toString();
    }

    @NotNull
    String getClassName() {
      resolveNames();
      return myClassName;
    }

    @NotNull
    String getMethodName() {
      resolveNames();
      return myMethodName;
    }

    private void resolveNames() {
      if (myResolvedNames) {
        return;
      }

      assert myMethodId != INVALID_METHOD_ID;
      StackFrameInfoResponse frameInfo = myCaptureObject.getClient().getStackFrameInfo(
        StackFrameInfoRequest.newBuilder().setProcessId(myCaptureObject.getProcessId()).setSession(myCaptureObject.getSession())
          .setMethodId(myMethodId).build()
      );

      myClassName = frameInfo.getClassName();
      myMethodName = frameInfo.getMethodName();
      myResolvedNames = true;
    }
  }
}

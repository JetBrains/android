/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.classifiers;

import com.android.tools.idea.codenavigation.CodeLocation;
import com.android.tools.profiler.proto.Memory.AllocationStack;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.google.common.base.Strings;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Arrays;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return methodClassifier(captureObject, 0);
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
    return methodClassifier(myCaptureObject, myCallstackDepth);
  }

  @NotNull
  public String getClassName() {
    return myMethodInfo.getClassName();
  }

  @NotNull
  public String getMethodName() {
    return myMethodInfo.getMethodName();
  }

  private static Classifier methodClassifier(CaptureObject captureObject, int depth) {
    return new Classifier.Join<>(getMethodInfo(captureObject, depth), info -> new MethodSet(captureObject, info, depth + 1),
                                 Classifier.of(InstanceObject::getClassEntry, ClassSet::new));
  }

  private static Function1<InstanceObject, MethodSetInfo> getMethodInfo(CaptureObject captureObject, int depth) {
    return inst -> {
      int stackDepth = inst.getCallStackDepth();
      if (stackDepth <= 0 || depth >= stackDepth) {
        return null;
      }
      int frameIndex = stackDepth - depth - 1;
      AllocationStack stack = inst.getAllocationCallStack();
      if (stack != null) {
        switch (stack.getFrameCase()) {
          case FULL_STACK:
            AllocationStack.StackFrameWrapper fullStack = stack.getFullStack();
            AllocationStack.StackFrame stackFrame = fullStack.getFrames(frameIndex);
            return new MethodSetInfo(captureObject, stackFrame.getClassName(), stackFrame.getMethodName());
          case ENCODED_STACK:
            AllocationStack.EncodedFrameWrapper smallStack = stack.getEncodedStack();
            AllocationStack.EncodedFrame smallFrame = smallStack.getFrames(frameIndex);
            return new MethodSetInfo(captureObject, smallFrame.getMethodId());
          default:
            throw new UnsupportedOperationException();
        }
      }

      assert frameIndex >= 0 && frameIndex < stackDepth;
      CodeLocation location = inst.getAllocationCodeLocations().get(frameIndex);
      return new MethodSetInfo(captureObject,
                               Strings.nullToEmpty(location.getClassName()),
                               Strings.nullToEmpty(location.getMethodName()));
    };
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
      if (!Strings.isNullOrEmpty(myClassName)) {
        builder.append(" (").append(myClassName).append(")");
      }
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
      AllocationStack.StackFrame frameInfo = myCaptureObject.getStackFrame(myMethodId);
      assert frameInfo != null;
      myClassName = frameInfo.getClassName();
      myMethodName = frameInfo.getMethodName();
      myResolvedNames = true;
    }
  }
}

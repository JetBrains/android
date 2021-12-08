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

import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import kotlin.jvm.functions.Function1;

final class NativeFunctionClassifier {

  public static Classifier of(int depth) {
    return new Classifier.Join<>(stackFrameAt(depth), frame -> new NativeCallStackSet(frame, depth + 1),
                                 Classifier.of(inst -> inst.getClassEntry().getClassName(), NativeAllocationMethodSet::new));
  }

  private static Function1<InstanceObject, Memory.AllocationStack.StackFrame> stackFrameAt(int depth) {
    return instance -> {
      int stackDepth = instance.getCallStackDepth();
      Memory.AllocationStack stack = instance.getAllocationCallStack();
      if (stackDepth <= 0 || depth >= stackDepth || stack == null) {
        return null;
      }

      int frameIndex = stackDepth - depth - 1;
      Memory.AllocationStack.StackFrameWrapper fullStack = stack.getFullStack();
      return fullStack.getFrames(frameIndex);
    };
  }
}
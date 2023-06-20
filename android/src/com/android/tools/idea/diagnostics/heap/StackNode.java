/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import java.util.NoSuchElementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StackNode {
  final int depth;
  @Nullable final Object obj;
  final boolean referencesProcessed;
  final long tag;

  private StackNode(@Nullable final Object obj, int depth, boolean referencesProcessed, long tag) {
    this.obj = obj;
    this.depth = depth;
    this.referencesProcessed = referencesProcessed;
    this.tag = tag;
  }

  @Nullable
  Object getObject() {
    return obj;
  }

  /**
   * This method caches the <a href="https://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html#jmethodID">MethodId</a> of the
   * StackNode constructor for future use. This caching allows to avoid the repeated method resolution and JVM method table requests.
   */
  static native void cacheStackNodeConstructorId(Class<?> stackNodeClass);

  /**
   * This method returns the top element of DFS native stack and marks the element as processed after returning.
   * Marking node as processed means that it was already processed and all the child objects were added to the stack. We always mark the
   * element after peeking it for the first time and joining this two methods allows to decrease the number of native calls and decrease
   * the overhead.
   * For the subsequent calls element is already marked as processed, so marking it again is not necessary, but won't break anything.
   */
  static native StackNode peekAndMarkProcessedDepthFirstSearchStack(Class<?> stackNodeClass);

  /**
   * This method pops the top element of DFS native stack.
   */
  static native void popElementFromDepthFirstSearchStack() throws NoSuchElementException;

  /**
   * This method instantiate a new DFS node with passed Object, depth and tag and pushes it to the DFS native stack.
   */
  static native void pushElementToDepthFirstSearchStack(@NotNull final Object obj, int depth, long tag);

  /**
   * Clears the DFS native stack.
   */
  static native void clearDepthFirstSearchStack();

  /**
   * @return the current size of the DFS native stack.
   */
  static native int getDepthFirstSearchStackSize();
}

/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.skylark.debugger.impl;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import java.util.Collection;

class SkylarkSuspendContext extends XSuspendContext {

  private final SkylarkExecutionStack activeStack;
  private final SkylarkDebugProcess debugProcess;

  SkylarkSuspendContext(SkylarkDebugProcess debugProcess, PausedThreadState threadInfo) {
    this.debugProcess = debugProcess;
    activeStack = new SkylarkExecutionStack(debugProcess, threadInfo);
  }

  @Override
  public SkylarkExecutionStack getActiveExecutionStack() {
    return activeStack;
  }

  @Override
  public XExecutionStack[] getExecutionStacks() {
    final Collection<PausedThreadState> threads = debugProcess.getPausedThreads();
    if (threads.isEmpty()) {
      return XExecutionStack.EMPTY_ARRAY;
    }
    XExecutionStack[] stacks = new XExecutionStack[threads.size()];
    int i = 0;
    for (PausedThreadState thread : threads) {
      stacks[i++] = new SkylarkExecutionStack(debugProcess, thread);
    }
    return stacks;
  }
}

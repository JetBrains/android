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

import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.PauseReason;
import com.google.devtools.build.lib.starlarkdebugging.StarlarkDebuggingProtos.PausedThread;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import javax.annotation.Nullable;
import javax.swing.Icon;

class SkylarkExecutionStack extends XExecutionStack {

  private final SkylarkDebugProcess debugProcess;
  private final PausedThreadState threadInfo;

  SkylarkExecutionStack(SkylarkDebugProcess debugProcess, PausedThreadState threadInfo) {
    super(threadInfo.thread.getName(), getThreadIcon(threadInfo.thread));
    this.debugProcess = debugProcess;
    this.threadInfo = threadInfo;
  }

  @Nullable
  @Override
  public XStackFrame getTopFrame() {
    return null;
  }

  @Override
  public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
    if (firstFrameIndex != 0) {
      // already computed
      return;
    }
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              debugProcess.listFrames(threadInfo.thread.getId(), container);
            });
  }

  long getThreadId() {
    return threadInfo.thread.getId();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SkylarkExecutionStack)) {
      return false;
    }
    return threadInfo.thread.getId() == ((SkylarkExecutionStack) obj).threadInfo.thread.getId();
  }

  @Override
  public int hashCode() {
    return (int) threadInfo.thread.getId();
  }

  private static Icon getThreadIcon(PausedThread threadInfo) {
    if (threadInfo.getPauseReason() == PauseReason.HIT_BREAKPOINT
        || threadInfo.getPauseReason() == PauseReason.CONDITIONAL_BREAKPOINT_ERROR) {
      return AllIcons.Debugger.ThreadAtBreakpoint;
    }
    return AllIcons.Debugger.ThreadSuspended;
  }
}

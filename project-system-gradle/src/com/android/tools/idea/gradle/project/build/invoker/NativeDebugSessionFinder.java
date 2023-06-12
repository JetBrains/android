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
package com.android.tools.idea.gradle.project.build.invoker;

import com.android.tools.idea.run.AndroidNativeDebugProcess;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class NativeDebugSessionFinder {
  @NotNull private final XDebuggerManager myDebuggerManager;

  NativeDebugSessionFinder(@NotNull Project project) {
    this(XDebuggerManager.getInstance(project));
  }

  @VisibleForTesting
  NativeDebugSessionFinder(@NotNull XDebuggerManager debuggerManager) {
    myDebuggerManager = debuggerManager;
  }

  /**
   * Returns the currently-running native debug session, if it exists.
   *
   * @return the native debug session or {@code null} if none exists.
   */
  @Nullable
  XDebugSession findNativeDebugSession() {
    for (XDebugSession session : myDebuggerManager.getDebugSessions()) {
      XDebugProcess debugProcess = session.getDebugProcess();
      if (debugProcess instanceof AndroidNativeDebugProcess) {
        return session;
      }
    }
    return null;
  }
}

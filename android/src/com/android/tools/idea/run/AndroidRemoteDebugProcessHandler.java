/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;

import java.io.OutputStream;

/**
 * A {@link com.intellij.execution.process.ProcessHandler} associated with an Android app debug process.
 * When terminated, it stops the debug process and notifies all attached {@link DebugProcessListener}s of the termination.
 * It is also optionally terminated when the debug process detaches.
 * Like {@link AndroidProcessHandler}, it is destroyed when the user stops execution.
 * We use this instead of {@link com.intellij.debugger.engine.RemoteDebugProcessHandler} to retain
 * {@link AndroidProcessHandler}'s termination semantics when debugging Android processes.
 */
public class AndroidRemoteDebugProcessHandler extends ProcessHandler {

  private final Project myProject;
  private final boolean myDetachWhenDoneMonitoring;

  public AndroidRemoteDebugProcessHandler(Project project, boolean detachWhenDoneMonitoring) {
    myProject = project;
    myDetachWhenDoneMonitoring = detachWhenDoneMonitoring;
  }

  // This is copied from com.intellij.debugger.engine.RemoteDebugProcessHandler#startNotify
  // and modified to only terminate on debug detach if myDetachWhenDoneMonitoring is true.
  @Override
  public void startNotify() {
    final DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    final DebugProcessListener listener = new DebugProcessListener() {
      @Override
      public void processDetached(DebugProcess process, boolean closedByUser) {
        debugProcess.removeDebugProcessListener(this);
        if (myDetachWhenDoneMonitoring) {
          notifyProcessDetached();
        }
      }
    };
    debugProcess.addDebugProcessListener(listener);
    try {
      super.startNotify();
    }
    finally {
      if (debugProcess.isDetached()) {
        debugProcess.removeDebugProcessListener(listener);
        if (myDetachWhenDoneMonitoring) {
          notifyProcessDetached();
        }
      }
    }
  }

  @Override
  protected void destroyProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(true);
    }
    notifyProcessTerminated(0);
  }

  @Override
  protected void detachProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(false);
    }
    notifyProcessDetached();
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  @Override
  public boolean isSilentlyDestroyOnClose() {
    return false;
  }

  @Override
  public OutputStream getProcessInput() {
    return null;
  }
}

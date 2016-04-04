/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.TaskExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.io.BaseDataReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.concurrent.Future;

/**
 * The {@link com.android.tools.idea.avdmanager.EmulatorProcessHandler} is a custom process handler specific to handling
 * the emulator process. The majority of the code is derived from {@link com.intellij.execution.process.BaseOSProcessHandler}.
 *
 * Unlike other processes, the emulator executable is a wrapper process that is mainly used to spawn off the real emulator. So its
 * process handler should not terminate when the wrapper exits, but should wait for the spawned emulator to exit. This is achieved
 * by waiting for the stdout and stderr streams to be closed (unlike the default handlers that wait for the process to terminate).
 * This works since the spawned emulator inherits stdout and stderr from the wrapper, and keeps them open for as long as it is alive.
 */
public class EmulatorProcessHandler extends ProcessHandler implements TaskExecutor, KillableProcess {
  private static final Logger LOG = Logger.getInstance(EmulatorProcessHandler.class);

  @NotNull private final Process myProcess;
  @NotNull private final GeneralCommandLine myCommandLine;

  public EmulatorProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    myProcess = process;
    myCommandLine = commandLine;
  }

  @Override
  public void startNotify() {
    // Wait for both the stdout and stderr reader threads to finish and then indicate that the process has terminated
    addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(final ProcessEvent event) {
        try {
          String presentableName = CommandLineUtil.extractPresentableName(myCommandLine.getCommandLineString());
          final BaseDataReader stdoutReader = new EmulatorOutputReader(myProcess.getInputStream(), ProcessOutputTypes.STDOUT,
                                                                       presentableName);
          final BaseDataReader stderrReader = new EmulatorOutputReader(myProcess.getErrorStream(), ProcessOutputTypes.STDERR,
                                                                       presentableName);

          executeTask(new Runnable() {
            @Override
            public void run() {
              try {
                stderrReader.waitFor();
                stdoutReader.waitFor();
              }
              catch (InterruptedException ignore) {
              }
              finally {
                notifyProcessTerminated(0);
              }
            }
          });
        }
        finally {
          removeProcessListener(this);
        }
      }
    });

    super.startNotify();
  }

  @Override
  protected void destroyProcessImpl() {
    try {
      closeStreams();
    }
    finally {
      // The console UI doesn't actually provide a way to terminate the emulator, so this method is only called when the IDE is closed.
      // At that time, we retain the previous behavior of just letting the emulator run independently of the IDE. If we do want to allow
      // this, then we'd need to kill the emulator using the emulator console so that it doesn't leave stale .lock files around.
      // myProcess.destroy();
      notifyProcessTerminated(0);
    }
  }

  @Override
  protected void detachProcessImpl() {
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        closeStreams();
        notifyProcessDetached();
      }
    };

    executeTask(runnable);
  }

  @Override
  public boolean detachIsDefault() {
    return true; // keep emulator running even if the IDE quits
  }

  @Nullable
  @Override
  public OutputStream getProcessInput() {
    return myProcess.getOutputStream();
  }

  @Override
  public boolean isSilentlyDestroyOnClose() {
    return true; // do not prompt the user about whether the emulator should be killed when the IDE is closed
  }

  private void closeStreams() {
    try {
      myProcess.getOutputStream().close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  @NotNull
  @Override
  public Future<?> executeTask(@NotNull Runnable task) {
    return ApplicationManager.getApplication().executeOnPooledThread(task);
  }

  @Override
  public boolean canKillProcess() {
    return false;
  }

  @Override
  public void killProcess() {
  }

  private class EmulatorOutputReader extends BaseDataReader {
    private final BufferedReader myBufferedReader;
    private final Key myProcessOutputType;

    private EmulatorOutputReader(@NotNull InputStream stream, @NotNull Key processOutputType, @NotNull String presentableName) {
      super(BaseDataReader.SleepingPolicy.SIMPLE);

      // TODO: charset for the input stream reader?
      myBufferedReader = new BufferedReader(new InputStreamReader(stream));
      myProcessOutputType = processOutputType;

      start(presentableName);
    }

    @NotNull
    @Override
    protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
      return EmulatorProcessHandler.this.executeTask(runnable);
    }

    @Override
    protected boolean readAvailable() throws IOException {
      String line = myBufferedReader.readLine();
      if (line == null) { // indicates that the emulator has stopped and closed the stream
        stop();
        return false;
      }
      else if (line.isEmpty()) {
        return false;
      }
      else {
        notifyTextAvailable(line + "\n", myProcessOutputType);
        return true;
      }
    }

    @Override
    protected void close() throws IOException {
      myBufferedReader.close();
    }
  }
}

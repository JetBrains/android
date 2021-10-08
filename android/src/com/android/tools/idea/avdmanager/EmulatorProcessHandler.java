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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;

/**
 * The {@link EmulatorProcessHandler} is a custom process handler specific to handling
 * the emulator process.
 */
public class EmulatorProcessHandler extends BaseOSProcessHandler {
  private static final Logger LOG = Logger.getInstance(EmulatorProcessHandler.class);

  public EmulatorProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    super(process, commandLine.getCommandLineString(), null);
    addProcessListener(new ConsoleListener());
    ProcessTerminatedListener.attach(this);
  }

  @Override
  protected @NotNull BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.forMostlySilentProcess();
  }

  private class ConsoleListener extends ProcessAdapter {
    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      String text = event.getText();
      if (text != null) {
        LOG.info("Emulator: " + text.trim());
      }

      if (ProcessOutputType.SYSTEM.equals(outputType) && isProcessTerminated()) {
        Integer exitCode = getExitCode();
        if (exitCode != null && exitCode != 0) {
          LOG.warn("Emulator terminated with exit code " + exitCode);
        }
      }
    }
  }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.fd;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.FastDeployManager;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;

public class PatchDeployState implements RunProfileState {
  private final AndroidFacet myFacet;
  private final Collection<IDevice> myDevices;

  public PatchDeployState(@NotNull AndroidFacet facet, @NotNull Collection<IDevice> devices) {
    myFacet = facet;
    myDevices = devices;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    final ProcessHandler processHandler = new DefaultDebugProcessHandler();

    final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(myFacet.getModule().getProject());
    ConsoleView console = builder.getConsole();
    console.attachToProcess(processHandler);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        // @formatter:off
        String msg = String.format(Locale.US,
                                   "Incrementally updating app on the following %1$s: %2$s\n",
                                   StringUtil.pluralize("device", myDevices.size()),
                                   join(myDevices));
        // @formatter:on
        processHandler.notifyTextAvailable(msg, ProcessOutputTypes.STDOUT);

        for (IDevice device : myDevices) {
          FastDeployManager.pushChanges(device, myFacet);
        }

        processHandler.notifyTextAvailable("Incremental update complete.\n", ProcessOutputTypes.STDOUT);
        processHandler.destroyProcess();
      }
    });

    return new DefaultExecutionResult(console, processHandler);
  }

  private static String join(Collection<IDevice> devices) {
    StringBuilder sb = new StringBuilder(devices.size() * 10);

    for (IDevice device : devices) {
      if (sb.length() > 0) {
        sb.append(", ");
      }

      sb.append(device.getName());
    }

    return sb.toString().trim();
  }
}

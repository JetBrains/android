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
package com.android.tools.idea.run;

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
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.DefaultDebugProcessHandler;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Locale;

public class PatchDeployState implements RunProfileState {
  private final RunContentDescriptor myDescriptor;
  private final ProcessHandler myProcessHandler;
  private final AndroidFacet myFacet;
  private final Collection<IDevice> myDevices;

  public PatchDeployState(@NotNull RunContentDescriptor descriptor, @NotNull AndroidFacet facet, @NotNull Collection<IDevice> devices) {
    myDescriptor = descriptor;
    myProcessHandler = descriptor.getProcessHandler();
    myFacet = facet;
    myDevices = devices;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        // @formatter:off
        String msg = String.format(Locale.US,
                                   "%1$s: Incrementally updating app on the following %2$s: %3$s\n",
                                   new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime()),
                                   StringUtil.pluralize("device", myDevices.size()),
                                   join(myDevices));
        // @formatter:on
        myProcessHandler.notifyTextAvailable(msg, ProcessOutputTypes.STDOUT);

        for (IDevice device : myDevices) {
          FastDeployManager.pushChanges(device, myFacet);
        }

        myProcessHandler.notifyTextAvailable("Incremental update complete.\n", ProcessOutputTypes.STDOUT);
      }
    });

    return new DefaultExecutionResult(myDescriptor.getExecutionConsole(), myProcessHandler);
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

  public RunContentDescriptor getDescriptor() {
    return myDescriptor;
  }
}

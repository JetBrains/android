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
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Locale;

public class PatchDeployState implements RunProfileState {
  private static final Logger LOG = Logger.getInstance(PatchDeployState.class);

  private final RunContentDescriptor myDescriptor;
  private final String myApplicationId;
  private ProcessHandler myProcessHandler;
  private final AndroidFacet myFacet;
  private final Collection<IDevice> myDevices;

  public PatchDeployState(@NotNull RunContentDescriptor descriptor, @NotNull AndroidFacet facet, @NotNull Collection<IDevice> devices) {
    myDescriptor = descriptor;
    myProcessHandler = descriptor.getProcessHandler();
    myFacet = facet;
    myDevices = devices;

    try {
      myApplicationId = ApkProviderUtil.computePackageName(myFacet);
    }
    catch (ApkProvisionException e) {
      LOG.error("Unable to determine package name.");
      throw new IllegalArgumentException(e);
    }
  }


  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    assert false : "Should not be called..";
    return null;
  }

  public void start() throws ExecutionException {
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

        boolean coldSwap = FastDeployManager.isColdSwap(AndroidGradleModel.get(myFacet));
        AndroidMultiProcessHandler newProcessHandler = null;
        ProcessHandler oldProcessHandler = myProcessHandler;

        if (coldSwap) {
          LOG.info("Performing a cold swap, application will restart");
          myProcessHandler.notifyTextAvailable("Performing a cold swap, application will restart\n", ProcessOutputTypes.STDOUT);
          newProcessHandler = switchToNewProcessHandler();
        }

        for (IDevice device : myDevices) {
          FastDeployManager.pushChanges(device, myFacet);

          if (coldSwap) {
            newProcessHandler.addTargetDevice(device);
          }
        }

        myProcessHandler.notifyTextAvailable("Incremental update complete.\n", ProcessOutputTypes.STDOUT);

        if (coldSwap && (oldProcessHandler instanceof AndroidMultiProcessHandler)) {
          // We want to cleanup the existing process handler, but we don't want it to attempt to kill any clients
          ((AndroidMultiProcessHandler)oldProcessHandler).setNoKill();
          oldProcessHandler.destroyProcess();
        }
      }
    });
  }

  @NotNull
  private AndroidMultiProcessHandler switchToNewProcessHandler() {
    AndroidMultiProcessHandler handler = new AndroidMultiProcessHandler(myApplicationId);
    handler.startNotify();

    AndroidSessionInfo info = myProcessHandler.getUserData(AndroidDebugRunner.ANDROID_SESSION_INFO);
    if (info == null) {
      throw new IllegalStateException("Unexpected error: Old state was not an instance of an Android run.");
    }

    AndroidSessionInfo newSession = new AndroidSessionInfo(handler, myDescriptor, info.getState(), info.getExecutorId());
    handler.putUserData(AndroidDebugRunner.ANDROID_SESSION_INFO, newSession);

    ConsoleView console = info.getState().getConsoleView();
    if (console == null) {
      throw new IllegalStateException("Unexpected error: Old state was not attached to a console.");
    }

    console.attachToProcess(handler);

    myDescriptor.setProcessHandler(handler);
    myProcessHandler = handler;

    return handler;
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

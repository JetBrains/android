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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.FastDeployManager;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.execution.*;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PatchDeployState implements RunProfileState {
  private static final Logger LOG = Logger.getInstance(PatchDeployState.class);

  private final RunContentDescriptor myDescriptor;
  private final String myApplicationId;
  private final Executor myExecutor;
  private final ExecutionEnvironment myEnv;
  private final ConsoleProvider myConsoleProvider;
  private final AndroidRunConfigurationBase myConfiguration;
  private ProcessHandler myProcessHandler;
  private final AndroidFacet myFacet;
  private final Collection<IDevice> myDevices;

  public PatchDeployState(@NotNull Executor executor,
                          @NotNull ExecutionEnvironment env,
                          @NotNull AndroidRunConfigurationBase runConfiguration,
                          @NotNull ConsoleProvider consoleProvider,
                          @NotNull RunContentDescriptor descriptor,
                          @NotNull AndroidFacet facet,
                          @NotNull Collection<IDevice> devices) {
    myExecutor = executor;
    myEnv = env;
    myConfiguration = runConfiguration;
    myConsoleProvider = consoleProvider;
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

        AndroidGradleModel model = AndroidGradleModel.get(myFacet);
        boolean coldSwap = model != null && FastDeployManager.isColdSwap(model);
        if (coldSwap && !FastDeployManager.isColdSwapEnabled(myFacet.getModule().getProject())) {
          FastDeployManager.displayVerifierStatus(model, myFacet);
          LOG.info("Cold swap is not supported yet, restarting run configuration");
          myProcessHandler.destroyProcess();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Project project = myFacet.getModule().getProject();
              RunnerAndConfigurationSettings config = RunManager.getInstance(project)
                .createConfiguration(myConfiguration, AndroidRunConfigurationType.getInstance().getFactory());
              ProgramRunnerUtil.executeConfiguration(project, config, myExecutor);
            }
          });
          return;
        }

        if (coldSwap && (myProcessHandler instanceof AndroidMultiProcessHandler)) {
          LOG.info("Performing a cold swap, application will restart\n");
          myProcessHandler.notifyTextAvailable("Performing a cold swap, application will restart\n", ProcessOutputTypes.STDOUT);
          ((AndroidMultiProcessHandler)myProcessHandler).reset();
        }

        for (IDevice device : myDevices) {
          FastDeployManager.pushChanges(device, myFacet);

          if (coldSwap) {
            if (myProcessHandler instanceof AndroidMultiProcessHandler) {
              ((AndroidMultiProcessHandler)myProcessHandler).addTargetDevice(device);
            }
            else {
              // debugging: we need to listen for the client to come online, and then attach the debugger.
              final Client client = waitForClient(device, myApplicationId, 15, TimeUnit.SECONDS);
              if (client != null) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    launchDebug(client);
                  }
                });
              }
            }
          }
        }

        myProcessHandler.notifyTextAvailable("Incremental update complete.\n", ProcessOutputTypes.STDOUT);
      }
    });
  }

  private void launchDebug(@NotNull Client client) {
    String debugPort = Integer.toString(client.getDebuggerListenPort());
    RemoteConnection connection = new RemoteConnection(true, "localhost", debugPort, false);
    PatchAndroidDebugState debugState =
      new PatchAndroidDebugState(myFacet.getModule().getProject(), myExecutor, connection, myConsoleProvider, myConfiguration,
                                 client.getDevice());

    myProcessHandler.detachProcess();
    ExecutionEnvironment env = new ExecutionEnvironmentBuilder(myEnv)
      .executor(myExecutor)
      .runner(myEnv.getRunner())
      .contentToReuse(myDescriptor)
      .build();

    RunContentDescriptor debugDescriptor;
    try {
      debugDescriptor = DebuggerPanelsManager.getInstance(myFacet.getModule().getProject())
        .attachVirtualMachine(env, debugState, debugState.getRemoteConnection(), false);
    }
    catch (ExecutionException e) {
      LOG.error("Error connecting to debugger", e);
      return;
    }

    if (debugDescriptor == null) {
      LOG.info("Cannot start debugger");
      return;
    }

    ProcessHandler handler = debugDescriptor.getProcessHandler();
    if (handler == null) {
      LOG.error("Cannot get debug process handler");
      return;
    }

    handler.putUserData(AndroidDebugRunner.ANDROID_SESSION_INFO, new AndroidSessionInfo(handler, debugDescriptor, debugState, myExecutor.getId()));
    handler.putUserData(AndroidDebugRunner.ANDROID_DEBUG_CLIENT, client);
  }

  // Note: this is a hack to get debugging working during cold swap. It should be merged along with AndroidDebugRunner eventually.
  // Rather than listening for ddmlib events, this just polls for the given timeout.
  @Nullable
  private static Client waitForClient(@NotNull IDevice device, @NotNull String packageId, long timeout, @NotNull  TimeUnit unit) {
    for (int i = 0; i < unit.toSeconds(timeout); i++) { // cold swaps seem to take a while
      Client client = device.getClient(packageId);
      if (client != null && client.isValid()) {
        return client;
      }

      try {
        TimeUnit.SECONDS.sleep(1);
      }
      catch (InterruptedException e) {
        return null;
      }
    }

    return null;
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

  /** HACK ALERT: This is a copy of {@link AndroidDebugState} with some minor changes.. */
  private static class PatchAndroidDebugState implements RemoteState, AndroidExecutionState {
    private final Project myProject;
    private final ConsoleProvider myConsoleProvider;
    private final Executor myExecutor;
    private final RemoteConnection myRemoteConnection;
    private final IDevice myDevice;
    private final AndroidRunConfigurationBase myConfiguration;
    private ConsoleView myConsoleView;

    public PatchAndroidDebugState(@NotNull Project project,
                                  @NotNull Executor executor,
                                  @NotNull RemoteConnection connection,
                                  @NotNull ConsoleProvider consoleProvider,
                                  @NotNull AndroidRunConfigurationBase configuration,
                                  @NotNull IDevice device) {
      myProject = project;
      myExecutor = executor;
      myConsoleProvider = consoleProvider;
      myRemoteConnection = connection;
      myConfiguration = configuration;
      myDevice = device;
    }

    @Nullable
    @Override
    public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
      RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject);
      myConsoleView = myConsoleProvider.createAndAttach(myProject, process, myExecutor);
      return new DefaultExecutionResult(myConsoleView, process);
    }

    @Override
    public RemoteConnection getRemoteConnection() {
      return myRemoteConnection;
    }

    @Nullable
    @Override
    public Collection<IDevice> getDevices() {
      return Collections.singletonList(myDevice);
    }

    @Nullable
    @Override
    public ConsoleView getConsoleView() {
      return myConsoleView;
    }

    @Override
    public int getRunConfigurationId() {
      return myConfiguration.getUniqueID();
    }

    @NotNull
    @Override
    public String getRunConfigurationTypeId() {
      return myConfiguration.getType().getId();
    }
  }
}

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
package com.android.tools.idea.fd;

import com.android.SdkConstants;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.fd.client.InstantRunClient;
import com.android.tools.fd.client.InstantRunPushFailedException;
import com.android.tools.fd.client.UpdateMode;
import com.android.tools.idea.run.AndroidProgramRunner;
import com.android.tools.idea.run.InstalledPatchCache;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableSet;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * The {@linkplain InstantRunManager} is responsible for handling Instant Run related functionality
 * in the IDE: determining if an app is running with the fast deploy runtime, whether it's up to date, communicating with it, etc.
 */
public final class InstantRunManager implements ProjectComponent {
  public static final String MINIMUM_GRADLE_PLUGIN_VERSION_STRING = SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;
  public static final GradleVersion MINIMUM_GRADLE_PLUGIN_VERSION = GradleVersion.parse(MINIMUM_GRADLE_PLUGIN_VERSION_STRING);
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("InstantRun", ToolWindowId.RUN);

  public static final Logger LOG = Logger.getInstance("#InstantRun");
  public static final ILogger ILOGGER = new LogWrapper(LOG);

  /**
   * White list of processes whose presence will not disable hotswap.
   *
   * Instant Run (hotswap) does not work with multiple processes right now. If we detect that the app uses multiple processes,
   * we always force a cold swap. However, a common scenario is where an app uses multiple processes, but just for the purpose of
   * a 3rd party library (e.g. leakcanary). In this case, we are ok doing a hotswap to just the main process (assuming that the
   * main process starts up first).
   */
  public static final ImmutableSet<String> ALLOWED_MULTI_PROCESSES = ImmutableSet.of(":leakcanary");

  @NotNull private final Project myProject;
  @NotNull private final FileChangeListener myFileChangeListener;

  /** Don't call directly: this is a project component instantiated by the IDE; use {@link #get(Project)} instead! */
  @SuppressWarnings("WeakerAccess") // Called by infrastructure
  public InstantRunManager(@NotNull Project project) {
    myProject = project;
    myFileChangeListener = new FileChangeListener(project);
    myFileChangeListener.setEnabled(InstantRunSettings.isInstantRunEnabled());
  }

  /** Returns the per-project instance of the fast deploy manager */
  @NotNull
  public static InstantRunManager get(@NotNull Project project) {
    //noinspection ConstantConditions
    return project.getComponent(InstantRunManager.class);
  }

  @NotNull
  public static AndroidVersion getMinDeviceApiLevel(@NotNull ProcessHandler processHandler) {
    AndroidVersion version = processHandler.getUserData(AndroidProgramRunner.ANDROID_DEVICE_API_LEVEL);
    return version == null ? AndroidVersion.DEFAULT : version;
  }

  /**
   * Returns the build id in the project as seen by the IDE
   *
   * @return the build id, if found
   */
  @Nullable
  private static String getLocalBuildTimestamp(@NotNull InstantRunContext context) {
    InstantRunBuildInfo buildInfo = context.getInstantRunBuildInfo();
    return buildInfo == null ? null : buildInfo.getTimeStamp();
  }

  /**
   * Called after a build &amp; successful push to device: updates the build id on the device to whatever the
   * build id was assigned by Gradle.
   *
   * @param device the device to push to
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   */
  public static void transferLocalIdToDeviceId(@NotNull IDevice device, @NotNull InstantRunContext context) {
    String buildId = getLocalBuildTimestamp(context);
    assert !StringUtil.isEmpty(buildId) : "Unable to detect build timestamp";

    InstantRunClient.transferBuildIdToDevice(device, buildId, context.getApplicationId(), ILOGGER);
  }

  /** Returns true if the device is capable of running Instant Run */
  public static boolean isInstantRunCapableDeviceVersion(@NotNull AndroidVersion version) {
    return version.getApiLevel() >= 15;
  }

  public static boolean hasLocalCacheOfDeviceData(@NotNull IDevice device, @NotNull InstantRunContext context) {
    InstalledPatchCache cache = context.getInstalledPatchCache();
    return cache.getInstalledManifestResourcesHash(device, context.getApplicationId()) != null;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "InstantRunManager";
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  public FileChangeListener.Changes getChangesAndReset() {
    return myFileChangeListener.getChangesAndReset();
  }

  /** Synchronizes the file listening state with whether instant run is enabled */
  static void updateFileListener(@NotNull Project project) {
    InstantRunManager manager = get(project);
    manager.myFileChangeListener.setEnabled(InstantRunSettings.isInstantRunEnabled());
  }

  @Nullable
  public static InstantRunClient getInstantRunClient(@NotNull InstantRunContext context) {
    InstantRunBuildInfo buildInfo = context.getInstantRunBuildInfo();
    if (buildInfo == null) {
      // we always obtain the secret token from the build info, and if a build info doesn't exist,
      // there is no point connecting to the app, we'll be doing a clean build anyway
      return null;
    }

    return new InstantRunClient(context.getApplicationId(), ILOGGER, buildInfo.getSecretToken());
  }

  /**
   * Pushes the artifacts obtained from the {@link InstantRunContext} to the given device.
   * If the app is running, the artifacts are sent directly to the server running as part of the app.
   * Otherwise, we save it to a file on the device.
   */
  public UpdateMode pushArtifacts(@NotNull IDevice device,
                                  @NotNull InstantRunContext context,
                                  @NotNull UpdateMode updateMode) throws InstantRunPushFailedException, IOException {
    InstantRunClient client = getInstantRunClient(context);
    assert client != null;

    InstantRunBuildInfo instantRunBuildInfo = context.getInstantRunBuildInfo();
    assert instantRunBuildInfo != null;

    updateMode = client.pushPatches(device,
                                    instantRunBuildInfo,
                                    updateMode,
                                    InstantRunSettings.isRestartActivity(),
                                    InstantRunSettings.isShowToastEnabled());

    if ((updateMode == UpdateMode.HOT_SWAP || updateMode == UpdateMode.WARM_SWAP)) {
      refreshDebugger(context.getApplicationId());
    }

    return updateMode;
  }

  private void refreshDebugger(@NotNull String packageName) {
    // First we reapply the breakpoints on the new code, otherwise the breakpoints
    // remain set on the old classes and will never be hit again.
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        DebuggerManagerEx debugger = DebuggerManagerEx.getInstanceEx(myProject);
        if (!debugger.getSessions().isEmpty()) {
          List<Breakpoint> breakpoints = debugger.getBreakpointManager().getBreakpoints();
          for (Breakpoint breakpoint : breakpoints) {
            if (breakpoint.isEnabled()) {
              breakpoint.setEnabled(false);
              breakpoint.setEnabled(true);
            }
          }
        }
      }
    });

    // Now we refresh the call-stacks and the variable panes.
    DebuggerManagerEx debugger = DebuggerManagerEx.getInstanceEx(myProject);
    for (final DebuggerSession session : debugger.getSessions()) {
      Client client = session.getProcess().getProcessHandler().getUserData(AndroidProgramRunner.ANDROID_DEBUG_CLIENT);
      if (client != null && client.isValid() && StringUtil.equals(packageName, client.getClientData().getClientDescription())) {
        session.getProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            DebuggerContextImpl context = session.getContextManager().getContext();
            SuspendContextImpl suspendContext = context.getSuspendContext();
            if (suspendContext != null) {
              XExecutionStack stack = suspendContext.getActiveExecutionStack();
              if (stack != null) {
                ((JavaExecutionStack)stack).initTopFrame();
              }
            }
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                session.refresh(false);
                XDebugSession xSession = session.getXDebugSession();
                if (xSession != null) {
                  xSession.resume();
                }
              }
            });
          }
        });
      }
    }
  }
}

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
package com.android.tools.idea.profilers;

import com.android.ddmlib.IDevice;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.profilers.perfd.ProfilerServiceProxy;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.*;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsManager;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.function.Predicate;

public class AndroidProfilerToolWindow extends AspectObserver implements Disposable {

  /**
   * Key for storing the last app that was run when the profiler window was not opened. This allows the Profilers to start auto-profiling
   * that app when the user opens the window at a later time.
   */
  static final Key<PreferredProcessInfo> LAST_RUN_APP_INFO = Key.create("Profiler.Last.Run.App");

  private static final String HIDE_STOP_PROMPT = "profilers.hide.stop.prompt";

  private static final String OPEN_FILE_FAILURE_BALLOON_TITLE = "Failed to open file";

  private static final String OPEN_FILE_FAILURE_BALLOON_TEXT = "The profiler was unable to open the selected file. Please try opening it " +
                                                               "again or select a different file.";

  @NotNull
  private final StudioProfilersView myView;
  @NotNull
  private final StudioProfilers myProfilers;
  @NotNull
  private final ToolWindow myWindow;
  @NotNull
  private final Project myProject;
  @NotNull
  private final IntellijProfilerComponents myIdeProfilerComponents;
  @NotNull
  private final AndroidProfilerWindowManagerListener myToolWindowManagerListener;

  public AndroidProfilerToolWindow(@NotNull ToolWindow window, @NotNull Project project) {
    myWindow = window;
    myProject = project;

    ProfilerClient client = null;
    IdeProfilerServices ideProfilerServices = new IntellijProfilerServices(myProject);
    ProfilerService service = ProfilerService.getInstance(myProject);
    if (service != null) {
      service.getDataStoreService().setNoPiiExceptionHanlder(ideProfilerServices::reportNoPiiException);
      client = service.getProfilerClient();
    }
    myProfilers = new StudioProfilers(client, ideProfilerServices);

    // Attempt to find the last-run process and start profiling it. This covers the case where the user presses "Run" (without profiling),
    // but then opens the profiling window manually.
    PreferredProcessInfo processInfo = myProject.getUserData(LAST_RUN_APP_INFO);
    if (processInfo != null) {
      myProfilers.setPreferredProcess(processInfo.deviceName, processInfo.processName, processInfo.processFilter);
      myProject.putUserData(LAST_RUN_APP_INFO, null);
    }
    else {
      // Note the always-false predicate, which prevents the Profilers from randomly start profiling.
      StartupManager
        .getInstance(project)
        .runWhenProjectIsInitialized(() -> myProfilers.setPreferredProcess(null, getPreferredProcessName(myProject), p -> false));
    }

    myIdeProfilerComponents = new IntellijProfilerComponents(myProject, myProfilers.getIdeServices().getFeatureTracker());
    myView = new StudioProfilersView(myProfilers, myIdeProfilerComponents);
    Disposer.register(this, myView);

    myProfilers.addDependency(this)
               .onChange(ProfilerAspect.MODE, this::modeChanged)
               .onChange(ProfilerAspect.STAGE, this::stageChanged);
    myProfilers.getSessionsManager().addDependency(this)
               .onChange(SessionAspect.SELECTED_SESSION, this::selectedSessionChanged)
               .onChange(SessionAspect.PROFILING_SESSION, this::profilingSessionChanged);

    myToolWindowManagerListener = new AndroidProfilerWindowManagerListener();
    ToolWindowManagerEx.getInstanceEx(myProject).addToolWindowManagerListener(myToolWindowManagerListener);
  }

  @NotNull
  StudioProfilers getProfilers() {
    return myProfilers;
  }

  /**
   * Sets the profiler's auto-profiling process in case it has been unset.
   *
   * @param module           The module being profiled.
   * @param device           The target {@link IDevice} that the app will launch in.
   * @param processPredicate Additional filter used for choosing the most desirable process. e.g. Process of a particular pid,
   *                         or process that starts after a certain time.
   */
  public void profile(@NotNull PreferredProcessInfo processInfo) {
    myProfilers.setPreferredProcess(processInfo.deviceName, processInfo.processName, processInfo.processFilter);
  }

  /**
   * Disables auto device+process selection.
   * See: {@link StudioProfilers#setAutoProfilingEnabled(boolean)}
   */
  void disableAutoProfiling() {
    myProfilers.setAutoProfilingEnabled(false);
  }

  /**
   * Tries to import a file into an imported session of the profilers and shows an error balloon if it fails to do so.
   */
  public void openFile(@NotNull VirtualFile file) {
    if (!myProfilers.getSessionsManager().importSessionFromFile(new File(file.getPath()))) {
      myProfilers.getIdeServices().showErrorBalloon(OPEN_FILE_FAILURE_BALLOON_TITLE, OPEN_FILE_FAILURE_BALLOON_TEXT, null, null);
    }
  }

  private void modeChanged() {
    ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
    boolean maximize = myProfilers.getMode() == ProfilerMode.EXPANDED;
    if (maximize != manager.isMaximized(myWindow)) {
      manager.setMaximized(myWindow, maximize);
    }
  }

  private void stageChanged() {
    if (myProfilers.isStopped()) {
      AndroidProfilerToolWindowFactory.removeContent(myWindow);
      ToolWindowManagerEx.getInstanceEx(myProject).removeToolWindowManagerListener(myToolWindowManagerListener);
    }
  }

  private void selectedSessionChanged() {
    Common.SessionMetaData metaData = myProfilers.getSessionsManager().getSelectedSessionMetaData();
    // setTitle appends to the ToolWindow's existing name (i.e. "Profiler"), hence we only
    // need to create and set the string for the session's name.
    myWindow.setTitle(metaData.getSessionName());
  }

  private void profilingSessionChanged() {
    Common.Session profilingSession = myProfilers.getSessionsManager().getProfilingSession();
    if (SessionsManager.isSessionAlive(profilingSession)) {
      myWindow.setIcon(ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER));
    }
    else {
      myWindow.setIcon(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER);
    }
  }

  @Override
  public void dispose() {
    myProfilers.removeDependencies(this);
    myProfilers.stop();
  }

  @NotNull
  public JComponent getComponent() {
    return myView.getComponent();
  }

  @Nullable
  private static String getPreferredProcessName(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      String moduleName = getModuleName(module);
      if (moduleName != null) {
        return moduleName;
      }
    }
    return null;
  }

  @Nullable
  static String getModuleName(@NotNull Module module) {
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(module);
    if (moduleInfo != null) {
      String pkg = moduleInfo.getPackage();
      if (pkg != null) {
        return pkg;
      }
    }
    return null;
  }

  /**
   * Analogous to {@link StudioProfilers#buildDeviceName(Common.Device)} but works with an {@link IDevice} instead.
   *
   * @return A string of the format: {Manufacturer Model}.
   */
  @NotNull
  static String getDeviceDisplayName(@NotNull IDevice device) {
    StringBuilder deviceNameBuilder = new StringBuilder();
    String manufacturer = ProfilerServiceProxy.getDeviceManufacturer(device);
    String model = ProfilerServiceProxy.getDeviceModel(device);
    String serial = device.getSerialNumber();
    String suffix = String.format("-%s", serial);
    if (model.endsWith(suffix)) {
      model = model.substring(0, model.length() - suffix.length());
    }
    if (!StringUtil.isEmpty(manufacturer)) {
      deviceNameBuilder.append(manufacturer);
      deviceNameBuilder.append(" ");
    }
    deviceNameBuilder.append(model);

    return deviceNameBuilder.toString();
  }

  /**
   * This class maps 1-to-1 with an {@link AndroidProfilerToolWindow} instance.
   */
  private class AndroidProfilerWindowManagerListener extends ToolWindowManagerAdapter {
    private boolean myIsProfilingActiveBalloonShown = false;
    private boolean myWasWindowExpanded = false;

    /**
     * How the profilers should respond to the tool window's state changes is as follow:
     * 1. If the window is hidden while a session is running, we prompt to user whether they want to stop the session.
     * If yes, we stop and kill the profilers. Otherwise, the hide action is undone and the tool strip button remain shown.
     * 2. If the window is minimized while a session is running, a balloon is shown informing users that the profilers is still running.
     */
    @Override
    public void stateChanged() {
      // We need to query the tool window again, because it might have been unregistered when closing the project.
      ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(AndroidProfilerToolWindowFactory.ID);
      if (window == null) {
        return;
      }

      boolean hasAliveSession = SessionsManager.isSessionAlive(myProfilers.getSessionsManager().getProfilingSession());

      boolean isWindowTabHidden = !window.isShowStripeButton(); // Profilers is removed from the toolbar.
      boolean isWindowExpanded = window.isVisible(); // Profiler window is expanded.
      boolean windowVisibilityChanged = isWindowExpanded != myWasWindowExpanded;
      myWasWindowExpanded = isWindowExpanded;
      if (isWindowTabHidden) {
        if (hasAliveSession) {
          boolean hidePrompt = myProfilers.getIdeServices().getTemporaryProfilerPreferences().getBoolean(HIDE_STOP_PROMPT, false);
          boolean confirm = hidePrompt || myIdeProfilerComponents.createUiMessageHandler().displayOkCancelMessage(
            "Confirm Stop Profiling",
            "Hiding the window will stop the current profiling session. Are you sure?",
            "Yes",
            "Cancel",
            null,
            result -> myProfilers.getIdeServices().getTemporaryProfilerPreferences().setBoolean(HIDE_STOP_PROMPT, result)
          );

          if (!confirm) {
            window.setShowStripeButton(true);
            return;
          }
        }

        myProfilers.stop();
        return;
      }

      if (isWindowExpanded) {
        myIsProfilingActiveBalloonShown = false;
        if (windowVisibilityChanged) {
          PreferredProcessInfo processInfo = myProject.getUserData(LAST_RUN_APP_INFO);
          if (processInfo != null && Common.Session.getDefaultInstance().equals(myProfilers.getSession())) {
            myProfilers.setPreferredProcess(processInfo.deviceName, processInfo.processName, processInfo.processFilter);
          }
        }
      }
      else {
        myProfilers.setAutoProfilingEnabled(false);
        if (hasAliveSession && !myIsProfilingActiveBalloonShown) {
          // Only shown the balloon if we detect the window is hidden for the first time.
          myIsProfilingActiveBalloonShown = true;
          String messageHtml = "A profiler session is running in the background.<br>" +
                               (myProfilers.getIdeServices().getFeatureConfig().isSessionsEnabled() ?
                                "To end the session, open the profiler and click the stop button in the Sessions pane." :
                                "To end the session, open the profiler and click the \"End Session\" button");
          ToolWindowManager.getInstance(myProject).notifyByBalloon(AndroidProfilerToolWindowFactory.ID, MessageType.INFO, messageHtml);
        }
      }
    }
  }

  static class PreferredProcessInfo {
    @NotNull private final String deviceName;
    @NotNull private final String processName;
    @NotNull private final Predicate<Common.Process> processFilter;

    PreferredProcessInfo(@NotNull String deviceName, @NotNull String processName, @NotNull Predicate<Common.Process> processFilter) {
      this.deviceName = deviceName;
      this.processName = processName;
      this.processFilter = processFilter;
    }
  }
}

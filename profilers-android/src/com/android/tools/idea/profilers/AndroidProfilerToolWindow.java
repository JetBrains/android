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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.function.Predicate;

public class AndroidProfilerToolWindow extends AspectObserver implements Disposable {

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
  private final ProfilerLayeredPane myLayeredPane;
  @NotNull
  private final IntellijProfilerComponents myIdeProfilerComponents;
  @NotNull
  private final AndroidProfilerWindowManagerListener myToolWindowManagerListener;

  public AndroidProfilerToolWindow(@NotNull ToolWindow window, @NotNull Project project) {
    myWindow = window;
    myProject = project;

    ProfilerService service = ProfilerService.getInstance(myProject);
    ProfilerClient client = service.getProfilerClient();
    myProfilers = new StudioProfilers(client, new IntellijProfilerServices(myProject));

    // Sets the preferred process. Note the always-false predicate, which prevents the Profilers to immediately starts profiling an app that
    // is already running.
    StartupManager.getInstance(project)
                  .runWhenProjectIsInitialized(
                    () -> myProfilers.setPreferredProcess(null,
                                                          getPreferredProcessName(myProject),
                                                          p -> false));

    myIdeProfilerComponents = new IntellijProfilerComponents(myProject, myProfilers.getIdeServices().getFeatureTracker());
    myView = new StudioProfilersView(myProfilers, myIdeProfilerComponents);
    myLayeredPane = new ProfilerLayeredPane();
    service.getDataStoreService().setNoPiiExceptionHanlder(myProfilers.getIdeServices()::reportNoPiiException);
    initializeUi();
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

  private void initializeUi() {
    JComponent content = myView.getComponent();
    myLayeredPane.add(content, JLayeredPane.DEFAULT_LAYER);
    myLayeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        content.setBounds(0, 0, myLayeredPane.getWidth(), myLayeredPane.getHeight());
        content.revalidate();
        content.repaint();
      }
    });
  }

  /**
   * Sets the profiler's auto-profiling process in case it has been unset.
   *
   * @param module           The module being profiled.
   * @param device           The target {@link IDevice} that the app will launch in.
   * @param processPredicate Additional filter used for choosing the most desirable process. e.g. Process of a particular pid,
   *                         or process that starts after a certain time.
   */
  public void profileModule(@NotNull Module module, @NotNull IDevice device, @Nullable Predicate<Common.Process> processPredicate) {
    myProfilers.setPreferredProcess(getDeviceDisplayName(device), getModuleName(module), processPredicate);
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

  public JComponent getComponent() {
    return myLayeredPane;
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
  private static String getModuleName(@NotNull Module module) {
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
  public static String getDeviceDisplayName(@NotNull IDevice device) {
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

      boolean isWindowHidden = !window.isShowStripeButton();
      if (isWindowHidden) {
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

      boolean isWindowVisible = window.isVisible();
      if (isWindowVisible) {
        myIsProfilingActiveBalloonShown = false;
      }
      else if (hasAliveSession && !myIsProfilingActiveBalloonShown) {
        // Only shown the balloon if we detect the window is hidden for the first time.
        myIsProfilingActiveBalloonShown = true;
        ToolWindowManager.getInstance(myProject).notifyByBalloon(
          AndroidProfilerToolWindowFactory.ID,
          MessageType.INFO,
          "Profiler session is running in the background. Hide the toolbar button to stop profiling completely."
        );
      }
    }
  }
}

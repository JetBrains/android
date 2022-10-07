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

import static com.android.tools.profilers.ProfilerFonts.H1_FONT;
import static com.android.tools.profilers.ProfilerFonts.STANDARD_FONT;

import com.android.ddmlib.IDevice;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.idea.codenavigation.CodeNavigator;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.transport.TransportService;
import com.android.tools.idea.transport.TransportServiceProxy;
import com.android.tools.nativeSymbolizer.ProjectSymbolSource;
import com.android.tools.nativeSymbolizer.SymbolFilesLocator;
import com.android.tools.nativeSymbolizer.SymbolSource;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.Notification;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
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
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import icons.StudioIcons;
import icons.StudioIllustrations;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidProfilerToolWindow implements Disposable {

  /**
   * Key for storing the last app that was run when the profiler window was not opened. This allows the Profilers to start auto-profiling
   * that app when the user opens the window at a later time.
   */
  static final Key<PreferredProcessInfo> LAST_RUN_APP_INFO = Key.create("Profiler.Last.Run.App");

  private static final String HIDE_STOP_PROMPT = "profilers.hide.stop.prompt";

  @NotNull
  private static final Notification OPEN_FILE_FAILURE_NOTIFICATION = new Notification(
    Notification.Severity.ERROR,
    "Failed to open file",
    "The profiler was unable to open the selected file. Please try opening it " +
    "again or select a different file.",
    null);

  private static final String NO_CLIENT_TITLE = "Initialization failed";
  private static final String NO_CLIENT_MESSAGE = "To start the profiler, close all other Android Studio projects.";

  @NotNull
  private final JPanel myPanel;
  @Nullable
  private StudioProfilersWrapper myProfilersWrapper;
  @NotNull
  private final ToolWindowWrapper myWindow;
  @NotNull
  private final Project myProject;
  @NotNull
  private final IntellijProfilerServices myIdeProfilerServices;

  public AndroidProfilerToolWindow(@NotNull ToolWindowWrapper window, @NotNull Project project) {
    myWindow = window;
    myProject = project;

    SymbolSource symbolSource = new ProjectSymbolSource(project);
    SymbolFilesLocator symbolLocator = new SymbolFilesLocator(symbolSource);
    myIdeProfilerServices = new IntellijProfilerServices(myProject, symbolLocator);
    Disposer.register(this, myIdeProfilerServices);

    myPanel = new JPanel(new BorderLayout());
    if (!tryInitializeProfilers()) {
      myIdeProfilerServices.getFeatureTracker().trackProfilerInitializationFailed();
      myPanel.add(buildInitializationFailedUi());
    }
  }

  /**
   * Attempt to create the {@link StudioProfilers} and its facilities. Note that the StudioProfilers will not be re-created if one already
   * exists, or if the profilers is already running in a separate project.
   *
   * @return true if the StudioProfilers already exists or is successfully created. False otherwise.
   */
  private boolean tryInitializeProfilers() {
    if (myProfilersWrapper != null) {
      return true;
    }

    TransportService service = TransportService.getInstance();
    if (service == null) {
      return false;
    }
    myProfilersWrapper = new StudioProfilersWrapper(myProject, myWindow, service, myIdeProfilerServices);
    Disposer.register(this, myProfilersWrapper);
    myPanel.removeAll();
    myPanel.add(myProfilersWrapper.getProfilersView().getComponent());
    myPanel.revalidate();
    myPanel.repaint();

    return true;
  }

  @NotNull
  private JComponent buildInitializationFailedUi() {
    JPanel panel = new JPanel();
    BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
    panel.setLayout(layout);
    panel.add(Box.createVerticalGlue());
    panel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    JLabel icon = new JLabel(StudioIllustrations.Common.DISCONNECT_PROFILER);
    icon.setHorizontalAlignment(SwingConstants.CENTER);
    icon.setAlignmentX(Component.CENTER_ALIGNMENT);
    panel.add(icon);

    JLabel title = new JLabel(NO_CLIENT_TITLE);
    title.setHorizontalAlignment(SwingConstants.CENTER);
    title.setAlignmentX(Component.CENTER_ALIGNMENT);
    title.setFont(H1_FONT);
    title.setForeground(ProfilerColors.MESSAGE_COLOR);
    panel.add(title);
    panel.add(Box.createRigidArea(new Dimension(1, 15)));

    JLabel message = new JLabel(NO_CLIENT_MESSAGE);
    message.setHorizontalAlignment(SwingConstants.CENTER);
    message.setAlignmentX(Component.CENTER_ALIGNMENT);
    message.setFont(STANDARD_FONT);
    message.setForeground(ProfilerColors.MESSAGE_COLOR);
    panel.add(message);
    panel.add(Box.createVerticalGlue());

    return panel;
  }

  /**
   * @return The {@link StudioProfilers} instance. Null if the profilers cannot be initialized, such as if it is already opened in another
   * project.
   */
  @Nullable
  public StudioProfilers getProfilers() {
    return myProfilersWrapper != null ? myProfilersWrapper.getProfilers() : null;
  }

  /** Sets the profiler's auto-profiling process in case it has been unset. */
  public void profile(@NotNull PreferredProcessInfo processInfo) {
    if (tryInitializeProfilers()) {
      StudioProfilers profilers = myProfilersWrapper.getProfilers();
      profilers
        .setPreferredProcess(processInfo.getDeviceName(), processInfo.getProcessName(), p -> processInfo.getProcessFilter().invoke(p));
    }
  }

  /**
   * Disables auto device+process selection.
   * See: {@link StudioProfilers#setAutoProfilingEnabled(boolean)}
   */
  void disableAutoProfiling() {
    if (tryInitializeProfilers()) {
      StudioProfilers profilers = myProfilersWrapper.getProfilers();
      profilers.setAutoProfilingEnabled(false);
    }
  }

  /**
   * Tries to import a file into an imported session of the profilers and shows an error balloon if it fails to do so.
   */
  public void openFile(@NotNull VirtualFile file) {
    if (tryInitializeProfilers()) {
      StudioProfilers profilers = myProfilersWrapper.getProfilers();
      if (!profilers.getSessionsManager().importSessionFromFile(new File(file.getPath()))) {
        profilers.getIdeServices().showNotification(OPEN_FILE_FAILURE_NOTIFICATION);
      }
    }
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
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
    String manufacturer = TransportServiceProxy.getDeviceManufacturer(device);
    String model = TransportServiceProxy.getDeviceModel(device);
    String serial = device.getSerialNumber();
    return getDeviceDisplayName(manufacturer, model, serial);
  }

  /**
   * Gets the display name of a device with the given manufacturer, model, and serial string.
   */
  @NotNull
  public static String getDeviceDisplayName(@NotNull String manufacturer, @NotNull String model, @NotNull String serial) {
    StringBuilder deviceNameBuilder = new StringBuilder();
    String suffix = String.format("-%s", serial);
    if (model.endsWith(suffix)) {
      model = model.substring(0, model.length() - suffix.length());
    }
    if (!StringUtil.isEmpty(manufacturer) && !model.toUpperCase(Locale.US).startsWith(manufacturer.toUpperCase(Locale.US))) {
      deviceNameBuilder.append(manufacturer);
      deviceNameBuilder.append(" ");
    }
    deviceNameBuilder.append(model);

    return deviceNameBuilder.toString();
  }

  private static class StudioProfilersWrapper extends AspectObserver implements Disposable {
    @NotNull private final Project myProject;
    @NotNull private final ToolWindowWrapper myWindow;
    @NotNull private final StudioProfilers myProfilers;
    @NotNull private final StudioProfilersView myView;

    StudioProfilersWrapper(@NotNull Project project,
                           @NotNull ToolWindowWrapper window,
                           @NotNull TransportService service,
                           @NotNull IntellijProfilerServices ideProfilerServices) {
      myProject = project;
      myWindow = window;
      ProfilerClient client = new ProfilerClient(TransportService.getChannelName());
      myProfilers = new StudioProfilers(client, ideProfilerServices);
      CodeNavigator navigator = ideProfilerServices.getCodeNavigator();
      // CPU ABI architecture, when needed by the code navigator, should be retrieved from StudioProfiler selected session.
      navigator.setCpuArchSource(() ->
        myProfilers.getSessionsManager().getSelectedSessionMetaData().getProcessAbi()
      );

      myProfilers.addDependency(this)
        .onChange(ProfilerAspect.STAGE, this::stageChanged);
      myProfilers.getSessionsManager().addDependency(this)
        .onChange(SessionAspect.SELECTED_SESSION, this::selectedSessionChanged)
        .onChange(SessionAspect.PROFILING_SESSION, this::profilingSessionChanged);

      // Attempt to find the last-run process and start profiling it. This covers the case where the user presses "Run" (without profiling),
      // but then opens the profiling window manually.
      PreferredProcessInfo processInfo = myProject.getUserData(LAST_RUN_APP_INFO);
      if (processInfo != null) {
        myProfilers
          .setPreferredProcess(processInfo.getDeviceName(), processInfo.getProcessName(), p -> processInfo.getProcessFilter().invoke(p));
        myProject.putUserData(LAST_RUN_APP_INFO, null);
      }
      else {
        StartupManager
          .getInstance(myProject)
          .runWhenProjectIsInitialized(() -> myProfilers.setPreferredProcessName(getPreferredProcessName(myProject)));
      }

      IdeProfilerComponents profilerComponents =
        new IntellijProfilerComponents(myProject, myProfilers.getIdeServices().getFeatureTracker());
      myView = new StudioProfilersView(myProfilers, profilerComponents);
      Disposer.register(this, myView);

      myProject.getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC,
                                                        new AndroidProfilerWindowManagerListener(myProject, myProfilers, myView));
    }

    @Override
    public void dispose() {
      myProfilers.stop();
    }

    @NotNull
    private StudioProfilers getProfilers() {
      return myProfilers;
    }

    @NotNull
    private StudioProfilersView getProfilersView() {
      return myView;
    }

    private void stageChanged() {
      if (myProfilers.isStopped()) {
        myWindow.removeContent();
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
  }

  /**
   * This class maps 1-to-1 with an {@link AndroidProfilerToolWindow} instance.
   */
  private static class AndroidProfilerWindowManagerListener implements ToolWindowManagerListener {
    private boolean myIsProfilingActiveBalloonShown = false;
    private boolean myWasWindowExpanded = false;
    @NotNull private final Project myProject;
    @NotNull private final StudioProfilers myProfilers;
    @NotNull private final StudioProfilersView myProfilersView;

    AndroidProfilerWindowManagerListener(@NotNull Project project, @NotNull StudioProfilers profilers, @NotNull StudioProfilersView view) {
      myProject = project;
      myProfilers = profilers;
      myProfilersView = view;
    }

    /**
     * How the profilers should respond to the tool window's state changes is as follow:
     * 1. If the window is hidden while a session is running, we prompt to user whether they want to stop the session.
     * If yes, we stop and kill the profilers. Otherwise, the hide action is undone and the tool strip button remain shown.
     * 2. If the window is minimized while a session is running, a balloon is shown informing users that the profilers is still running.
     */
    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      // We need to query the tool window again, because it might have been unregistered when closing the project.
      ToolWindow window = toolWindowManager.getToolWindow(AndroidProfilerToolWindowFactory.ID);
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
          boolean confirm = hidePrompt || myProfilersView.getIdeProfilerComponents().createUiMessageHandler().displayOkCancelMessage(
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
            myProfilers.setPreferredProcess(processInfo.getDeviceName(), processInfo.getProcessName(),
                                            p -> processInfo.getProcessFilter().invoke(p));
          }
        }
      }
      else {
        myProfilers.setAutoProfilingEnabled(false);
        if (hasAliveSession && !myIsProfilingActiveBalloonShown) {
          // Only shown the balloon if we detect the window is hidden for the first time.
          myIsProfilingActiveBalloonShown = true;
          String messageHtml = "A profiler session is running in the background.<br>" +
                               "To end the session, open the profiler and click the stop button in the Sessions pane.";
          ToolWindowManager.getInstance(myProject).notifyByBalloon(AndroidProfilerToolWindowFactory.ID, MessageType.INFO, messageHtml);
        }
      }
    }
  }
}

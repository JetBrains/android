/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.idea.logcat;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.AndroidEnvironmentUtils;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.run.ShowLogcatListener;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ProjectTopics;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.messages.MessageBusConnection;
import java.awt.EventQueue;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLogcatToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final Key<DevicePanel> DEVICES_PANEL_KEY = Key.create("DevicePanel");

  public AndroidLogcatToolWindowFactory() {
    if (!LogcatExperimentalSettings.getInstance().getLogcatV2Enabled()) {
      ColorSettingsPages.getInstance().registerPage(new AndroidLogcatColorPage());
    }
  }

  @Override
  public void init(@NotNull ToolWindow toolWindow) {
    Project project = ((ToolWindowEx)toolWindow).getProject();
    project.getMessageBus().connect(project)
      .subscribe(ShowLogcatListener.TOPIC, new ShowLogcatListener() {
        @Override
        public void showLogcat(@NotNull ShowLogcatListener.DeviceInfo deviceInfo, @Nullable String applicationId) { }

        @Override
        public void showLogcat(@NotNull IDevice device, @Nullable String applicationId) {
           AndroidLogcatToolWindowFactory.this.showLogcat(toolWindow, device, applicationId);
        }
      });
  }

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return !LogcatExperimentalSettings.getInstance().getLogcatV2Enabled() && AndroidEnvironmentUtils.isAndroidEnvironment(project);
  }

  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
    // In order to use the runner layout ui, the runner infrastructure needs to be initialized.
    // Otherwise it is not possible to for example drag one of the tabs out of the tool window.
    // The object that needs to be created is the content manager of the execution manager for this project.
    RunContentManager.getInstance(project);

    toolWindow.setAvailable(true);
    toolWindow.setToHideOnEmptyContent(true);

    LogcatPanel logcatPanel = new LogcatPanel(project, toolWindow);
    AndroidLogcatView logcatView = logcatPanel.getLogcatView();

    MessageBusConnection busConnection = project.getMessageBus().connect(toolWindow.getDisposable());
    busConnection.subscribe(ToolWindowManagerListener.TOPIC, new MyToolWindowManagerListener(project, logcatView));
    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyAndroidPlatformListener(logcatView));

    final ContentManager contentManager = toolWindow.getContentManager();
    Content c = contentManager.getFactory().createContent(logcatPanel, "", true);

    // Store references to the device panel view, so that it can be retrieved directly from
    // the DDMS tool window. (e.g. to select a particular device, etc)
    c.putUserData(DEVICES_PANEL_KEY, logcatPanel.getDevicePanel());

    contentManager.addContent(c);

    ApplicationManager.getApplication().invokeLater(() -> {
      logcatView.activate();
      final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(getToolWindowId());
      if (window != null && window.isVisible()) {
        ConsoleView console = logcatView.getLogConsole().getConsole();
        if (console != null) {
          checkFacetAndSdk(project, console);
        }
      }
    }, project.getDisposed());

    final File adb = AndroidSdkUtils.getAdb(project);
    if (adb == null) {
      return;
    }

    logcatPanel.setLoadingText("Initializing ADB");
    logcatPanel.startLoading();

    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);
    Futures.addCallback(future, new FutureCallback<AndroidDebugBridge>() {
      @Override
      public void onSuccess(@Nullable AndroidDebugBridge bridge) {
        thisLogger().info("Successfully obtained debug bridge");
        logcatPanel.stopLoading();
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        logcatPanel.stopLoading();

        thisLogger().info("Unable to obtain debug bridge", t);
        Messages.showErrorDialog(AdbService.getDebugBridgeDiagnosticErrorMessage(t, adb), "ADB Connection Error");
      }
    }, EdtExecutorService.getInstance());
  }

  private void showLogcat(
    @NotNull ToolWindow toolWindow,
    @NotNull String serialNumber,
    @Nullable String applicationId) {

    final File adb = AndroidSdkUtils.getAdb(toolWindow.getProject());
    if (adb == null) {
      thisLogger().warn("Failed to show Logcat for device: adb not found");
      return;
    }
    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);

    Futures.addCallback(future, new FutureCallback<>() {
      @Override
      public void onSuccess(AndroidDebugBridge bridge) {
        Optional<IDevice> device = Arrays.stream(bridge.getDevices()).findFirst();
        if (device.isEmpty()) {
          thisLogger().info("Device not found: " + serialNumber);
          return;
        }
        showLogcat(toolWindow, device.get(), applicationId);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        thisLogger().warn("Failed to show Logcat for device " + serialNumber, t);
      }
    }, EdtExecutorService.getInstance());
  }

  private void showLogcat(
    @NotNull ToolWindow toolWindow,
    @NotNull IDevice device,
    @Nullable String applicationId) {
    EventQueue.invokeLater(() -> toolWindow.activate(() -> {
      int count = toolWindow.getContentManager().getContentCount();
      // There should never be more than a single content but this code works just as good as using getContent(0) protected by an if.
      for (int i = 0; i < count; i++) {
        Content content = toolWindow.getContentManager().getContent(i);
        DevicePanel devicePanel = content == null ? null : content.getUserData(AndroidLogcatToolWindowFactory.DEVICES_PANEL_KEY);
        if (devicePanel != null) {
          devicePanel.selectDevice(device);
          if (applicationId != null) {
            devicePanel.selectClient(device.getClient(applicationId));
          }
          break;
        }
      }
    }));
  }

  private static final class MyToolWindowManagerListener implements ToolWindowManagerListener {
    private final Project myProject;
    private final AndroidLogcatView myLogcatView;

    private boolean myToolWindowVisible;

    private MyToolWindowManagerListener(@NotNull Project project, @NotNull AndroidLogcatView logcatView) {
      myProject = project;
      myLogcatView = logcatView;
    }

    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      ToolWindow window = toolWindowManager.getToolWindow("Logcat");

      if (window == null) {
        return;
      }

      boolean visible = window.isVisible();

      if (myToolWindowVisible == visible) {
        return;
      }

      myToolWindowVisible = visible;
      myLogcatView.activate();

      if (!visible) {
        return;
      }

      ConsoleView consoleView = myLogcatView.getLogConsole().getConsole();

      if (consoleView == null) {
        return;
      }

      checkFacetAndSdk(myProject, consoleView);
    }
  }

  @NotNull
  public static String getToolWindowId() {
    return "Logcat";
  }

  private static void checkFacetAndSdk(Project project, @NotNull final ConsoleView console) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);

    if (facets.isEmpty()) {
      // No necessarily an issue, for example, a Flutter project.
      return;
    }

    final AndroidFacet facet = facets.get(0);
    AndroidPlatform platform = AndroidPlatform.getInstance(facet.getModule());
    if (platform == null) {
      console.clear();
      final Module module = facet.getModule();

      console.print("Please ", ConsoleViewContentType.ERROR_OUTPUT);
      console.printHyperlink("configure", p -> AndroidSdkUtils.openModuleDependenciesConfigurable(module));
      console.print(" Android SDK\n", ConsoleViewContentType.ERROR_OUTPUT);
    }
  }

  private static class MyAndroidPlatformListener implements ModuleRootListener {
    private final Project myProject;
    private final AndroidLogcatView myView;

    private AndroidPlatform myPrevPlatform;

    private MyAndroidPlatformListener(@NotNull AndroidLogcatView view) {
      myProject = view.getProject();
      myView = view;
      myPrevPlatform = getPlatform();
    }

    @Override
    public void rootsChanged(@NotNull ModuleRootEvent event) {
      final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(getToolWindowId());
      if (window == null) {
        return;
      }

      if (window.isDisposed() || !window.isVisible()) {
        return;
      }

      AndroidPlatform newPlatform = getPlatform();

      if (!Objects.equals(myPrevPlatform, newPlatform)) {
        myPrevPlatform = newPlatform;
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!window.isDisposed() && window.isVisible()) {
            myView.activate();
          }
        });
      }
    }

    @Nullable
    private AndroidPlatform getPlatform() {
      AndroidPlatform newPlatform = null;
      final List<AndroidFacet> facets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
      if (!facets.isEmpty()) {
        final AndroidFacet facet = facets.get(0);
        newPlatform = AndroidPlatform.getInstance(facet.getModule());
      }
      return newPlatform;
    }
  }

  @NotNull
  private Logger thisLogger() {
    return Logger.getInstance(AndroidLogcatToolWindowFactory.class);
  }
}

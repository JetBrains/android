/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.monitor;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.ClientData;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.ddms.OpenVmTraceHandler;
import com.android.tools.idea.ddms.actions.*;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.logcat.AndroidLogcatView;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ProjectTopics;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.messages.MessageBusConnection;
import icons.StudioIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class AndroidToolWindowFactory implements ToolWindowFactory, DumbAware, Condition<Project> {
  private static final String ANDROID_LOGCAT_CONTENT_ID = "Android Logcat";

  @NonNls private static final String ADBLOGS_CONTENT_ID = "AdbLogsContent";
  public static final Key<DevicePanel> DEVICES_PANEL_KEY = Key.create("DevicePanel");

  @Override
  public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
    // In order to use the runner layout ui, the runner infrastructure needs to be initialized.
    // Otherwise it is not possible to for example drag one of the tabs out of the tool window.
    // The object that needs to be created is the content manager of the execution manager for this project.
    ExecutionManager.getInstance(project).getContentManager();

    RunnerLayoutUi layoutUi = RunnerLayoutUi.Factory.getInstance(project).create("Android", getToolWindowId(), "Logcat", project);

    toolWindow.setAvailable(true, null);
    toolWindow.setToHideOnEmptyContent(true);

    DeviceContext deviceContext = new DeviceContext();

    // We should only set OpenVmTraceHandler as ClientData's IMethodProfilingHandler when using the old monitors,
    // because the new profilers use LegacyProfilingHandler instead.
    if (showMonitors()) {
      // TODO Remove global handlers. These handlers are global, but are set per project.
      // if there are two projects opened, things go very wrong.
      ClientData.setMethodProfilingHandler(new OpenVmTraceHandler(project));
    }

    final AndroidLogcatView logcatView = new AndroidLogcatView(project, deviceContext, getToolWindowId(), !showMonitors());
    Content logcatContent = createLogcatContent(layoutUi, project, logcatView);
    layoutUi.addContent(logcatContent, 0, PlaceInGrid.center, false);
    JPanel searchComponent = logcatView.createSearchComponent();

    DevicePanel devicePanel = new DevicePanel(project, deviceContext);
    JPanel northPanel = new JPanel(new BorderLayout());
    northPanel.add(devicePanel.getComponent(), BorderLayout.WEST);

    if (showMonitors()) {
      logcatContent.setSearchComponent(searchComponent);
      MonitorContentFactory.createMonitorContent(project, deviceContext, layoutUi);
      layoutUi.getOptions().setLeftToolbar(getToolbarActions(project, deviceContext), ActionPlaces.UNKNOWN);
      layoutUi.addListener(new ContentManagerAdapter() {
        @Override
        public void selectionChanged(ContentManagerEvent event) {
          Content selectedContent = event.getContent();
          BaseMonitorView view = selectedContent.getUserData(BaseMonitorView.MONITOR_VIEW_KEY);
          if (view != null && event.getOperation() == ContentManagerEvent.ContentOperation.add) {
            UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                             .setCategory(AndroidStudioEvent.EventCategory.PROFILING)
                                             .setKind(AndroidStudioEvent.EventKind.MONITOR_RUNNING)
                                             .setMonitorType(view.getMonitorType()));

          }
        }
      }, project);
    } else {
      searchComponent.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
      northPanel.add(searchComponent, BorderLayout.CENTER);
    }

    final JBLoadingPanel loadingPanel = new JBLoadingPanel(new BorderLayout(), project);
    northPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    loadingPanel.add(northPanel, BorderLayout.NORTH);
    loadingPanel.add(layoutUi.getComponent(), BorderLayout.CENTER);

    final ContentManager contentManager = toolWindow.getContentManager();
    Content c = contentManager.getFactory().createContent(loadingPanel, "", true);

    // Store references to the logcat & device panel views, so that these views can be retrieved directly from
    // the DDMS tool window. (e.g. to clear logcat before a launch, select a particular device, etc)
    c.putUserData(AndroidLogcatView.ANDROID_LOGCAT_VIEW_KEY, logcatView);
    c.putUserData(DEVICES_PANEL_KEY, devicePanel);

    contentManager.addContent(c);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        logcatView.activate();
        final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(getToolWindowId());
        if (window != null && window.isVisible()) {
          ConsoleView console = logcatView.getLogConsole().getConsole();
          if (console != null) {
            checkFacetAndSdk(project, console);
          }
        }
      }
    }, project.getDisposed());

    final File adb = AndroidSdkUtils.getAdb(project);
    if (adb == null) {
      return;
    }

    loadingPanel.setLoadingText("Initializing ADB");
    loadingPanel.startLoading();

    ListenableFuture<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);
    Futures.addCallback(future, new FutureCallback<AndroidDebugBridge>() {
      @Override
      public void onSuccess(@Nullable AndroidDebugBridge bridge) {
        Logger.getInstance(AndroidToolWindowFactory.class).info("Successfully obtained debug bridge");
        loadingPanel.stopLoading();
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        loadingPanel.stopLoading();

        Logger.getInstance(AndroidToolWindowFactory.class).info("Unable to obtain debug bridge", t);
        Messages.showErrorDialog(AdbService.getDebugBridgeDiagnosticErrorMessage(t, adb), "ADB Connection Error");
      }
    }, EdtExecutor.INSTANCE);
  }

  public static String getToolWindowId() {
    return showMonitors() ? "Android Monitor" : "Logcat";
  }

  @NotNull
  public ActionGroup getToolbarActions(Project project, DeviceContext deviceContext) {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new ScreenshotAction(project, deviceContext));
    group.add(new ScreenRecorderAction(project, deviceContext));
    group.add(DumpSysActions.create(project, deviceContext));
    group.add(new Separator());

    group.add(new TerminateVMAction(deviceContext));
    group.add(new LayoutInspectorAction(project, deviceContext));
    group.add(new Separator());
    group.add(new BrowserHelpAction("Android Monitor", "http://developer.android.com/r/studio-ui/android-monitor.html"));

    return group;
  }

  private static Content createLogcatContent(RunnerLayoutUi layoutUi, final Project project, AndroidLogcatView logcatView) {
    ToolWindowManagerEx.getInstanceEx(project).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      boolean myToolWindowVisible;

      @Override
      public void stateChanged() {
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(getToolWindowId());
        if (window != null) {
          boolean visible = window.isVisible();
          if (visible != myToolWindowVisible) {
            myToolWindowVisible = visible;
            logcatView.activate();
            if (visible) {
              ConsoleView console = logcatView.getLogConsole().getConsole();
              if (console != null) {
                checkFacetAndSdk(project, console);
              }
            }
          }
        }
      }
    });

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyAndroidPlatformListener(logcatView));

    JPanel logcatContentPanel = logcatView.getContentPanel();

    final Content logcatContent =
      layoutUi.createContent(ANDROID_LOGCAT_CONTENT_ID, logcatContentPanel, "logcat", StudioIcons.Shell.ToolWindows.LOGCAT, null);
    logcatContent.putUserData(AndroidLogcatView.ANDROID_LOGCAT_VIEW_KEY, logcatView);
    logcatContent.setDisposer(logcatView);
    logcatContent.setCloseable(false);
    logcatContent.setPreferredFocusableComponent(logcatContentPanel);

    return logcatContent;
  }

  private static void checkFacetAndSdk(Project project, @NotNull final ConsoleView console) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);

    if (facets.isEmpty()) {
      console.clear();
      console.print(AndroidBundle.message("android.logcat.no.android.facets.error"), ConsoleViewContentType.ERROR_OUTPUT);
      return;
    }

    final AndroidFacet facet = facets.get(0);
    AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      console.clear();
      final Module module = facet.getModule();

      if (!AndroidMavenUtil.isMavenizedModule(module)) {
        console.print("Please ", ConsoleViewContentType.ERROR_OUTPUT);
        console.printHyperlink("configure", new HyperlinkInfo() {
          @Override
          public void navigate(Project project) {
            AndroidSdkUtils.openModuleDependenciesConfigurable(module);
          }
        });
        console.print(" Android SDK\n", ConsoleViewContentType.ERROR_OUTPUT);
      }
      else {
        console.print(AndroidBundle.message("android.maven.cannot.parse.android.sdk.error", module.getName()) + '\n',
                      ConsoleViewContentType.ERROR_OUTPUT);
      }
    }
  }

  static boolean showMonitors() {
    return !StudioFlags.PROFILER_ENABLED.get();
  }

  @Override
  public boolean value(Project project) {
    return !showMonitors();
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
    public void rootsChanged(ModuleRootEvent event) {
      final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(getToolWindowId());
      if (window == null) {
        return;
      }

      if (window.isDisposed() || !window.isVisible()) {
        return;
      }

      AndroidPlatform newPlatform = getPlatform();

      if (!Comparing.equal(myPrevPlatform, newPlatform)) {
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
        newPlatform = facet.getConfiguration().getAndroidPlatform();
      }
      return newPlatform;
    }
  }
}

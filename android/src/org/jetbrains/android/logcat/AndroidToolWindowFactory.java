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

package org.jetbrains.android.logcat;

import com.android.ddmlib.Log;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.DevicePanel;
import com.intellij.ProjectTopics;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.run.AndroidDebugRunner;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidToolWindowFactory implements ToolWindowFactory, DumbAware {
  public static final String TOOL_WINDOW_ID = AndroidBundle.message("android.logcat.title");

  @NonNls private static final String DEVICE_PANEL_CONTENT = "DevicePanelContent";
  @NonNls private static final String ADBLOGS_CONTENT_ID = "AdbLogsContent";
  private static final Key<DevicePanel> DEVICES_PANEL_KEY = Key.create("DevicePanel");

  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    RunnerLayoutUi layoutUi = RunnerLayoutUi.Factory.getInstance(project).create(
      "ddms", "ddms", "ddms", project);

    toolWindow.setIcon(AndroidIcons.AndroidToolWindow);
    toolWindow.setAvailable(true, null);
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.setTitle(TOOL_WINDOW_ID);

    DeviceContext deviceContext = new DeviceContext();

    Content devicesContent = createDeviceContent(layoutUi, project, deviceContext);
    Content logcatContent = createLogcatContent(layoutUi, project, deviceContext);
    Content adbLogsContent = createAdbLogsContent(layoutUi, project);

    final AndroidLogcatView logcatView = logcatContent.getUserData(
      AndroidLogcatView.ANDROID_LOGCAT_VIEW_KEY);
    final DevicePanel devicePanel = devicesContent.getUserData(DEVICES_PANEL_KEY);

    assert logcatView != null;
    assert devicePanel != null;

    // The search component is used only from the first content in a tab, so we set it on
    // the devicesContent instead of logcatContent
    devicesContent.setSearchComponent(logcatView.createSearchComponent(project));

    layoutUi.addContent(devicesContent, 0, PlaceInGrid.left, false);
    layoutUi.addContent(logcatContent, 0, PlaceInGrid.center, false);
    layoutUi.addContent(adbLogsContent, 1, PlaceInGrid.center, false);

    layoutUi.getOptions().setLeftToolbar(devicePanel.getToolbarActions(), ActionPlaces.UNKNOWN);

    final ContentManager contentManager = toolWindow.getContentManager();
    Content c = contentManager.getFactory().createContent(layoutUi.getComponent(), "DDMS", true);
    contentManager.addContent(c);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        logcatView.activate();
        final ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (window != null && window.isVisible()) {
          ConsoleView console = logcatView.getLogConsole().getConsole();
          if (console != null) {
            checkFacetAndSdk(project, console);
          }
        }
      }
    }, project.getDisposed());
  }

  private static Content createDeviceContent(RunnerLayoutUi layoutUi,
                                             Project project,
                                             DeviceContext deviceContext) {
    DevicePanel devicePanel = new DevicePanel(project, deviceContext);
    Content devicesContent = layoutUi.createContent(
      DEVICE_PANEL_CONTENT,
      devicePanel.getContentPanel(),
      AndroidBundle.message("android.ddms.devicepanel.title"),
      AndroidIcons.Android,
      null);
    devicesContent.setCloseable(false);
    devicesContent.putUserData(DEVICES_PANEL_KEY, devicePanel);
    return devicesContent;
  }

  private static Content createLogcatContent(RunnerLayoutUi layoutUi,
                                             final Project project,
                                             DeviceContext deviceContext) {
    final AndroidLogcatView logcatView = new AndroidLogcatView(project, deviceContext) {
      @Override
      protected boolean isActive() {
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        return window.isVisible();
      }
    };
    ToolWindowManagerEx.getInstanceEx(project).addToolWindowManagerListener(
      new ToolWindowManagerAdapter() {
        boolean myToolWindowVisible;

        @Override
        public void stateChanged() {
          ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
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
      layoutUi.createContent(AndroidDebugRunner.ANDROID_LOGCAT_CONTENT_ID,
                             logcatContentPanel,
                             "logcat",
                             AndroidIcons.Ddms.Logcat, null);
    logcatContent.putUserData(AndroidLogcatView.ANDROID_LOGCAT_VIEW_KEY, logcatView);
    logcatContent.setDisposer(logcatView);
    logcatContent.setCloseable(false);
    logcatContent.setPreferredFocusableComponent(logcatContentPanel);

    return logcatContent;
  }

  private Content createAdbLogsContent(RunnerLayoutUi layoutUi, Project project) {
    final ConsoleView console = new ConsoleViewImpl(project, false);
    Content adbLogsContent = layoutUi.createContent(ADBLOGS_CONTENT_ID, console.getComponent(),
                                                    AndroidBundle
                                                      .message("android.adb.logs.tab.title"), null,
                                                    null);
    adbLogsContent.setCloseable(false);

    //noinspection UnnecessaryFullyQualifiedName
    com.android.ddmlib.Log.setLogOutput(new Log.ILogOutput() {
      @Override
      public void printLog(Log.LogLevel logLevel, String tag, String message) {
        reportAdbLogMessage(logLevel, tag, message, console);
      }

      @Override
      public void printAndPromptLog(Log.LogLevel logLevel, String tag, String message) {
        // todo: should we show dialog?
        reportAdbLogMessage(logLevel, tag, message, console);
      }
    });

    return adbLogsContent;
  }

  private static void reportAdbLogMessage(Log.LogLevel logLevel, String tag, String message, @NotNull ConsoleView consoleView) {
    if (message == null) {
      return;
    }
    if (logLevel == null) {
      logLevel = Log.LogLevel.INFO;
    }

    if (logLevel == Log.LogLevel.ERROR || logLevel == Log.LogLevel.ASSERT) {
      AdbErrors.reportError(message, tag);
    }

    final ConsoleViewContentType contentType = toConsoleViewContentType(logLevel);
    if (contentType == null) {
      return;
    }

    final String fullMessage = tag != null ? tag + ": " + message : message;
    consoleView.print(fullMessage + '\n', contentType);
  }

  @Nullable
  private static ConsoleViewContentType toConsoleViewContentType(@NotNull Log.LogLevel logLevel) {
    switch (logLevel) {
      case VERBOSE:
        return null;
      case DEBUG:
        return null;
      case INFO:
        return ConsoleViewContentType.getConsoleViewType(AndroidLogcatConstants.INFO);
      case WARN:
        return ConsoleViewContentType.getConsoleViewType(AndroidLogcatConstants.WARNING);
      case ERROR:
        return ConsoleViewContentType.getConsoleViewType(AndroidLogcatConstants.ERROR);
      case ASSERT:
        return ConsoleViewContentType.getConsoleViewType(AndroidLogcatConstants.ASSERT);
      default:
        assert false : "Unknown log level " + logLevel;
    }
    return null;
  }

  private static void checkFacetAndSdk(Project project, @NotNull final ConsoleView console) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);

    if (facets.size() == 0) {
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

  private static class MyAndroidPlatformListener extends ModuleRootAdapter {
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
      final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);
      if (window == null) {
        return;
      }
      
      if (window.isDisposed() || !window.isVisible()) {
        return;
      }

      AndroidPlatform newPlatform = getPlatform();

      if (!Comparing.equal(myPrevPlatform, newPlatform)) {
        myPrevPlatform = newPlatform;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!window.isDisposed() && window.isVisible()) {
              myView.activate();
            }
          }
        });
      }
    }

    @Nullable
    private AndroidPlatform getPlatform() {
      AndroidPlatform newPlatform = null;
      final List<AndroidFacet> facets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
      if (facets.size() > 0) {
        final AndroidFacet facet = facets.get(0);
        newPlatform = facet.getConfiguration().getAndroidPlatform();
      }
      return newPlatform;
    }
  }
}

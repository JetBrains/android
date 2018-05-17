/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.actions.DeviceScreenshotAction;
import com.android.tools.idea.ddms.actions.ScreenRecorderAction;
import com.android.tools.idea.ddms.actions.TerminateVMAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.logcat.AndroidLogcatView.MyConfigureLogcatHeaderAction;
import com.android.tools.idea.logcat.AndroidLogcatView.MyRestartAction;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFormatter;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AndroidLogConsole extends LogConsoleBase {
  private static final String ACTION_ID_PREFIX = AndroidLogConsole.class.getSimpleName() + ".";
  private static final PluginId PLUGIN_ID = PluginId.getId("org.jetbrains.android");

  private final AndroidLogcatView myView;
  private final RegexFilterComponent myRegexFilterComponent = new RegexFilterComponent("LOG_FILTER_HISTORY", 5);
  private final AndroidLogcatPreferences myPreferences;

  @NotNull
  private final Project myProject;

  AndroidLogConsole(@NotNull Project project,
                    @NotNull AndroidLogFilterModel model,
                    @NotNull LogFormatter formatter,
                    @NotNull AndroidLogcatView view) {
    super(project, null, "", false, model, GlobalSearchScope.allScope(project), formatter);
    myProject = project;
    ConsoleView console = getConsole();
    ActionManager actionManager = ActionManager.getInstance();
    if (console instanceof ConsoleViewImpl) {
      ConsoleViewImpl c = ((ConsoleViewImpl)console);
      DeviceContext context = view.getDeviceContext();

      c.addCustomConsoleAction(new Separator());
      c.addCustomConsoleAction(registerAction(actionManager, new MyRestartAction(view)));
      c.addCustomConsoleAction(registerAction(actionManager, new MyConfigureLogcatHeaderAction(view)));

      if (StudioFlags.LOGCAT_SUPPRESSED_TAGS_ENABLE.get()) {
        c.addCustomConsoleAction(registerAction(actionManager, new SuppressLogTagsAction(context, this::refresh)));
      }

      // TODO: Decide if these should be part of the profiler window
      c.addCustomConsoleAction(new Separator());
      c.addCustomConsoleAction(registerAction(actionManager, new DeviceScreenshotAction(project, context)));
      c.addCustomConsoleAction(registerAction(actionManager, new ScreenRecorderAction(project, context)));
      c.addCustomConsoleAction(new Separator());
      c.addCustomConsoleAction(registerAction(actionManager, new TerminateVMAction(context)));

      c.addCustomConsoleAction(new Separator());
      c.addCustomConsoleAction(registerAction(actionManager,
                                              new BrowserHelpAction("logcat", "http://developer.android.com/r/studio-ui/am-logcat.html")));
    }

    myView = view;
    myPreferences = AndroidLogcatPreferences.getInstance(project);

    myRegexFilterComponent.setFilter(myPreferences.TOOL_WINDOW_CUSTOM_FILTER);
    myRegexFilterComponent.setIsRegex(myPreferences.TOOL_WINDOW_REGEXP_FILTER);
    myRegexFilterComponent.addRegexListener(filter -> {
      myPreferences.TOOL_WINDOW_CUSTOM_FILTER = filter.getFilter();
      myPreferences.TOOL_WINDOW_REGEXP_FILTER = filter.isRegex();
      model.updateCustomPattern(filter.getPattern());
    });
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * Register an action with the {@link ActionManager}. This makes the action show up in "Find Action" tool and Keymap etc'.
   * <p>
   * The id is derived from the classname.
   */
  static AnAction registerAction(ActionManager actionManager, AnAction action) {
    String id = ACTION_ID_PREFIX + action.getClass().getSimpleName();
    if (actionManager.getAction(id) != null) {
      actionManager.unregisterAction(id);
    }
    actionManager.registerAction(id, action, PLUGIN_ID);
    return action;
  }

  @Override
  public void dispose() {
    super.dispose();
    ActionManager actionManager = ActionManager.getInstance();
    List<String> actions = actionManager.getActionIdList(ACTION_ID_PREFIX);
    for (String action : actions) {
      actionManager.unregisterAction(action);
    }
  }

  @Override
  public boolean isActive() {
    return myView.isActive();
  }

  public @Nullable IDevice getSelectedDevice() {
    return myView.getSelectedDevice();
  }

  public void clearLogcat() {
    IDevice device = myView.getSelectedDevice();

    if (device == null) {
      return;
    }

    AndroidLogcatService.getInstance().clearLogcat(device, myView.getProject());
  }

  @NotNull
  public Component getLogFilterComboBox() {
    Container component = getSearchComponent();
    assert component != null;

    return component.getComponent(0);
  }

  @NotNull
  @Override
  public Component getTextFilterComponent() {
    return myRegexFilterComponent;
  }

  public void addLogLine(@NotNull String line) {
    super.addMessage(line);
  }

  /**
   * Clear the current logs and replay all old messages. This is useful to do if the display
   * format of the logs have changed, for example.
   */
  public void refresh() {
    // Even if we haven't changed any filter, calling this method quickly refreshes the log as a
    // side effect.
    onTextFilterChange();
  }
}

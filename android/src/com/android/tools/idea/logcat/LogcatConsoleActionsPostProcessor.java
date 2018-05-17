/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.execution.actions.ClearConsoleAction;
import com.intellij.execution.actions.ConsoleActionsPostProcessor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.project.DumbAwareAction;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * The logcat console is just another instance of a {@link ConsoleView}. As such it comes with a default
 * set of actions. This class customizes that list of actions to be relevant for logcat.
 */
public final class LogcatConsoleActionsPostProcessor extends ConsoleActionsPostProcessor {
  @Override
  @NotNull
  public AnAction[] postProcess(@NotNull ConsoleView console, AnAction[] actions) {
    if (!(console instanceof ConsoleViewImpl)) {
      return actions;
    }

    ConsoleViewImpl consoleImpl = (ConsoleViewImpl)console;
    if (!(consoleImpl.getParent() instanceof AndroidLogConsole)) {
      return actions;
    }

    return processActions((AndroidLogConsole)consoleImpl.getParent(), actions);
  }

  @Override
  @NotNull
  public AnAction[] postProcessPopupActions(@NotNull ConsoleView console, AnAction[] actions) {
    if (!(console instanceof ConsoleViewImpl)) {
      return actions;
    }

    ConsoleViewImpl consoleImpl = (ConsoleViewImpl)console;
    if (!(consoleImpl.getParent() instanceof AndroidLogConsole)) {
      return actions;
    }

    return processPopupActions((AndroidLogConsole)consoleImpl.getParent(), actions);
  }

  /**
   * Moves "clear all" and "Scroll to End" actions to the toolbar start.
   *
   * @see <a href="http://b.android.com/66626">Bug 66626</a>.
   */
  @NotNull
  private static AnAction[] processActions(@NotNull AndroidLogConsole console, AnAction[] actions) {
    List<AnAction> actionList = new ArrayList<>(actions.length);

    AnAction scrollToEndAction = null;

    // remove actions that don't make sense for logcat
    for (AnAction a : actions) {
      // remove the existing clear all action
      if (a instanceof ClearConsoleAction) {
        continue;
      }

      // remove the scroll to end action, we'll add it back at the top
      if (a instanceof ScrollToTheEndToolbarAction) {
        @SuppressWarnings("DialogTitleCapitalization")
        String message = "Scroll to the end. Clicking on a particular line stops scrolling and keeps that line visible.";
        a.getTemplatePresentation().setDescription(message);
        a.getTemplatePresentation().setText(message);
        scrollToEndAction = a;
        continue;
      }

      actionList.add(a);
    }

    if (scrollToEndAction != null) {
      actionList.add(0, scrollToEndAction);
    }

    // add logcat specific actions
    actionList.add(0, AndroidLogConsole.registerAction(ActionManager.getInstance(), new ClearLogCatAction(console)));

    return actionList.toArray(AnAction.EMPTY_ARRAY);
  }

  /**
   * Replaces standard "Clear All" action to "Clear Logcat" action
   */
  @NotNull
  private static AnAction[] processPopupActions(@NotNull AndroidLogConsole console, AnAction[] actions) {
    List<AnAction> resultActions = new ArrayList<>();
    for (AnAction action : actions) {
      if (action instanceof ClearConsoleAction) {
        resultActions.add(new ClearLogCatAction(console));
      }
      else {
        resultActions.add(action);
      }
    }
    AndroidLogcatPreferences preferences = AndroidLogcatPreferences.getInstance(console.getProject());
    if (StudioFlags.LOGCAT_SUPPRESSED_TAGS_ENABLE.get()) {
      if (preferences.LOGCAT_HEADER_FORMAT.getShowTag()) {
        resultActions.add(new SuppressLogTagsMenuAction(console::refresh));
      }
    }
    return resultActions.toArray(new AnAction[0]);
  }

  private static final class ClearLogCatAction extends DumbAwareAction {
    private final AndroidLogConsole myConsole;

    private ClearLogCatAction(@NotNull AndroidLogConsole console) {
      super(AndroidBundle.message("android.logcat.clear.log.action.title"),
            AndroidBundle.message("android.logcat.clear.log.action.tooltip"),
            AllIcons.Actions.GC);
      myConsole = console;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = e.getData(LangDataKeys.CONSOLE_VIEW) != null;
      if (myConsole.getOriginalDocument().length() == 0) {
        enabled = false;
      }
      // Check for !isOnline() because isOffline() doesn't check for DISCONNECTED state.
      if (myConsole.getSelectedDevice() == null || !myConsole.getSelectedDevice().isOnline()) {
        enabled = false;
      }
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myConsole.clearLogcat();
    }
  }
}

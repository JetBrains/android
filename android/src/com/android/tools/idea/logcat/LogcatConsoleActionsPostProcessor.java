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

import com.intellij.execution.actions.ConsoleActionsPostProcessor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The logcat console is just another instance of a {@link ConsoleView}. As such it comes with a default
 * set of actions. This class customizes that list of actions to be relevant for logcat.
 */
public final class LogcatConsoleActionsPostProcessor extends ConsoleActionsPostProcessor {
  @NotNull
  @Override
  public AnAction[] postProcess(@NotNull ConsoleView console, @NotNull AnAction[] actions) {
    if (!(console instanceof ConsoleViewImpl)) {
      return actions;
    }

    ConsoleViewImpl consoleImpl = (ConsoleViewImpl)console;
    if (!(consoleImpl.getParent() instanceof AndroidLogConsole)) {
      return actions;
    }

    return processActions((AndroidLogConsole)consoleImpl.getParent(), actions);
  }

  @NotNull
  @Override
  public AnAction[] postProcessPopupActions(@NotNull ConsoleView console, @NotNull AnAction[] actions) {
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
  private static AnAction[] processActions(@NotNull AndroidLogConsole console, @NotNull AnAction[] actions) {
    List<AnAction> actionList = new ArrayList<>(actions.length);

    AnAction scrollToEndAction = null;

    // remove actions that don't make sense for logcat
    for (AnAction a : actions) {
      // remove the existing clear all action
      if (a instanceof ConsoleViewImpl.ClearAllAction) {
        continue;
      }

      // remove the scroll to end action, we'll add it back at the top
      if (a instanceof ScrollToTheEndToolbarAction) {
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
    actionList.add(0, new ClearLogCatAction(console));

    return actionList.toArray(new AnAction[actionList.size()]);
  }

  /**
   * Replaces standard "Clear All" action to "Clear Logcat" action
   */
  @NotNull
  private static AnAction[] processPopupActions(@NotNull AndroidLogConsole console, @NotNull AnAction[] actions) {
    AnAction[] resultActions = new AnAction[actions.length];
    for (int i = 0; i < actions.length; ++i) {
      if (actions[i] instanceof ConsoleViewImpl.ClearAllAction) {
        resultActions[i] = new ClearLogCatAction(console);
      }
      else {
        resultActions[i] = actions[i];
      }
    }
    return resultActions;
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
    public void update(AnActionEvent e) {
      boolean enabled = e.getData(LangDataKeys.CONSOLE_VIEW) != null;
      Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null && editor.getDocument().getTextLength() == 0) {
        enabled = false;
      }
      e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myConsole.clearLogcat();
    }
  }
}

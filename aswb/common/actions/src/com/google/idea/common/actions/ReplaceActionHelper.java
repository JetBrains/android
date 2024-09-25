/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.common.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

/** Helper class to conditionally replace/hide existing actions. */
public class ReplaceActionHelper {

  /** Conditionally hides the action with the given ID, if one exists. */
  public static void conditionallyHideAction(
      ActionManager actionManager, String actionId, Predicate<Project> shouldHide) {
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction != null) {
      replaceAction(actionManager, actionId, new RemovedAction(oldAction, shouldHide));
    }
  }

  /**
   * Conditionally replaces the action with the given ID with the new action. If there's no existing
   * action with the given ID, the new action is registered, and conditionally visible.
   */
  public static void conditionallyReplaceAction(
      ActionManager actionManager,
      String actionId,
      AnAction newAction,
      Predicate<Project> shouldReplace) {
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction == null) {
      oldAction = new EmptyAction(false);
    }
    replaceAction(actionManager, actionId, new ReplacedAction(oldAction, newAction, shouldReplace));
  }

  /**
   * Registers a new action against the provided action ID, unregistering any existing action with
   * this ID, if one exists.
   */
  public static void replaceAction(
      ActionManager actionManager, String actionId, AnAction newAction) {
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction != null) {
      newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
      actionManager.replaceAction(actionId, newAction);
    } else {
      actionManager.registerAction(actionId, newAction);
    }
  }

  /** Wraps an action and makes it conditionally invisible. */
  private static class RemovedAction extends AnAction {

    private final AnAction delegate;
    private final Predicate<Project> shouldHide;

    private RemovedAction(AnAction delegate, Predicate<Project> shouldHide) {
      super(
          delegate.getTemplatePresentation().getTextWithMnemonic(),
          delegate.getTemplatePresentation().getDescription(),
          delegate.getTemplatePresentation().getIcon());
      this.delegate = delegate;
      this.shouldHide = shouldHide;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      delegate.actionPerformed(e);
    }

    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.OLD_EDT;
    }

    @Override
    public void update(AnActionEvent e) {
      Project project = e.getProject();
      if (project != null && shouldHide.test(project)) {
        e.getPresentation().setEnabledAndVisible(false);
      } else {
        // default to visible and enabled, to handle the case where the delegate's update method
        // doesn't make changes to this.
        e.getPresentation().setEnabledAndVisible(true);
        delegate.update(e);
      }
    }
  }

  /** Conditionally replaces one action with another. */
  private static class ReplacedAction extends AnAction {

    private final AnAction originalAction;
    private final AnAction replacementAction;
    private final Predicate<Project> shouldReplace;

    private ReplacedAction(
        AnAction originalAction, AnAction replacementAction, Predicate<Project> shouldReplace) {
      this.originalAction = originalAction;
      this.replacementAction = replacementAction;
      this.shouldReplace = shouldReplace;
    }

    @Override
    @NotNull
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.OLD_EDT;
    }

    @Override
    public void update(AnActionEvent e) {
      // default to visible and enabled, to handle the case where the delegate's update method
      // doesn't make changes to this.
      e.getPresentation().setEnabledAndVisible(true);
      Project project = e.getProject();
      if (project != null && shouldReplace.test(e.getProject())) {
        applyPresentation(e.getPresentation(), replacementAction.getTemplatePresentation());
        replacementAction.update(e);
      } else {
        applyPresentation(e.getPresentation(), originalAction.getTemplatePresentation());
        originalAction.update(e);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (shouldReplace.test(e.getProject())) {
        replacementAction.actionPerformed(e);
      } else {
        originalAction.actionPerformed(e);
      }
    }

    private static void applyPresentation(
        Presentation presentation, Presentation templatePresentation) {
      presentation.restoreTextWithMnemonic(templatePresentation);
      presentation.setDescription(templatePresentation.getDescription());
      presentation.setIcon(templatePresentation.getIcon());
    }
  }
}

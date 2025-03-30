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
package com.android.tools.idea.actions;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.base.Strings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps an {@linkplain ActionGroup} and makes it invisible when a Gradle-based Android project is open.
 */
public class AndroidActionGroupRemover extends ActionGroup {
  @NotNull private final ActionGroup myDelegate;

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  /**
   * Creates a new {@link AndroidActionGroupRemover}.
   *
   * @param delegate the action to hide/remove when having an open Gradle-based Android project.
   * @param backupText the text to set in this action, in case the the delegate action does not have any text yet.
   */
  public AndroidActionGroupRemover(@NotNull ActionGroup delegate, @NotNull String backupText) {
    super(delegate.getTemplatePresentation().getTextWithMnemonic(), delegate.getTemplatePresentation().getDescription(),
          delegate.getTemplatePresentation().getIcon());
    myDelegate = delegate;
    Presentation presentation = getTemplatePresentation();
    if (Strings.isNullOrEmpty(presentation.getText())) {
      presentation.setText(backupText);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myDelegate.actionPerformed(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project != null && ProjectSystemUtil.requiresAndroidModel(project)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    presentation.setEnabledAndVisible(true);
    // Update the text and icon of the delegate. The text in the delegate may be null, and some delegate actions (e.g. MakeModuleAction)
    // assume is never null.
    updateTextAndIcon(getTemplatePresentation(), myDelegate.getTemplatePresentation());
    myDelegate.update(e);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myDelegate.getChildren(e);
  }

  private static void updateTextAndIcon(@NotNull Presentation source, @NotNull Presentation destination) {
    destination.setText(source.getTextWithMnemonic());
    destination.setIcon(source.getIcon());
  }

}

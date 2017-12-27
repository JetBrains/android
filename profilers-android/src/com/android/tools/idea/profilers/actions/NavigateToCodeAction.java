/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.profilers.actions;

import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public final class NavigateToCodeAction extends AnAction {
  @NotNull private final Supplier<CodeLocation> myLocationSupplier;
  @NotNull private final CodeNavigator myCodeNavigator;

  public NavigateToCodeAction(@NotNull Supplier<CodeLocation> locationSupplier, @NotNull CodeNavigator codeNavigator) {
    myLocationSupplier = locationSupplier;
    myCodeNavigator = codeNavigator;

    String title = ActionsBundle.actionText("EditSource");
    Presentation presentation = getTemplatePresentation();
    presentation.setText(title);
    presentation.setIcon(AllIcons.Actions.EditSource);
    presentation.setDescription(ActionsBundle.actionDescription("EditSource"));
    // TODO shortcuts
    // setShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet());
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(myLocationSupplier.get() != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    CodeLocation location = myLocationSupplier.get();
    if (location != null) {
      myCodeNavigator.navigate(location);
    }
  }
}

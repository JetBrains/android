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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_EXPAND_ALL;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.treeStructure.Tree;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBaseExpandAllAction extends DumbAwareAction {
  protected AbstractBaseExpandAllAction(@NotNull Tree tree) {
    this(tree, AllIcons.Actions.Expandall);
  }

  protected AbstractBaseExpandAllAction(@NotNull Tree tree, @NotNull Icon icon) {
    super("Expand All", "", icon);
    registerCustomShortcutSet(ActionManager.getInstance().getAction(ACTION_EXPAND_ALL).getShortcutSet(), tree);
  }
}

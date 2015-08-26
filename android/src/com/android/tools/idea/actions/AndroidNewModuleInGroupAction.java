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

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ide.projectView.impl.ModuleGroup.ARRAY_DATA_KEY;
import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY;

public class AndroidNewModuleInGroupAction extends AndroidNewModuleAction {
  public AndroidNewModuleInGroupAction() {
    super("Module", "Adds a new module to the project", null);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    ModuleGroup[] moduleGroups = e.getData(ARRAY_DATA_KEY);
    Module[] modules = e.getData(MODULE_CONTEXT_ARRAY);
    e.getPresentation().setVisible(isNotEmpty(moduleGroups) || isNotEmpty(modules));
  }

  private static boolean isNotEmpty(@Nullable Object[] array) {
    return array != null && array.length > 0;
  }
}

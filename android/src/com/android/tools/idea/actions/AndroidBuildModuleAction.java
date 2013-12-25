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

import com.intellij.compiler.actions.CompileActionBase;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This action fixes the "update" mechanism of the "Make Module(s)" and "Compile Module(s)" actions.
 * <ul>
 * <li>"Make Module(s)" action's {@code update} throws a NPE when wrapped by another action (in Android Studio)</li>
 * <li>"Compile Module(s)" action's {@code update} allows users to compile single files (e.g. .java files) but when using Gradle it makes
 * more sense to compile the module, rather than a single file.
 * </li>
 * <p/>
 * </ul>
 */
public abstract class AndroidBuildModuleAction extends AndroidBuildProjectAction {
  @NotNull private final String myActionName;

  protected AndroidBuildModuleAction(@NotNull CompileActionBase delegate, @NotNull String backupText, @NotNull String actionName) {
    super(delegate, backupText);
    myActionName = actionName;
  }

  protected void updatePresentation(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    Module[] modules = getSelectedModules(dataContext);
    int moduleCount = modules == null ? 0 : modules.length;
    boolean hasModules = moduleCount > 0;

    Presentation presentation = e.getPresentation();
    presentation.setEnabled(hasModules);

    String presentationText;
    if (hasModules) {
      String text = myActionName + (moduleCount == 1 ? " Module" : " Modules");
      for (int i = 0; i < moduleCount; i++) {
        if (text.length() > 30) {
          text = myActionName + " Selected Modules";
          break;
        }
        Module toMake = modules[i];
        if (i != 0) {
          text += ",";
        }
        text += " '" + toMake.getName() + "'";
      }
      presentationText = text;
    }
    else {
      presentationText = myActionName;
    }
    presentation.setText(presentationText);
    presentation.setVisible(hasModules || !ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace()));
  }

  @Nullable
  private static Module[] getSelectedModules(@NotNull DataContext dataContext) {
    Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modules != null) {
      return modules;
    }

    Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module != null) {
      return new Module[] { module };
    }

    return null;
  }
}

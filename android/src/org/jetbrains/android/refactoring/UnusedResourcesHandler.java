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
package org.jetbrains.android.refactoring;

import com.google.common.collect.Sets;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public class UnusedResourcesHandler implements RefactoringActionHandler {
  public static void invoke(final Project project,
                            @Nullable Module[] modules,
                            @Nullable final String filter,
                            boolean skipDialog,
                            boolean skipIds) {
    if (modules == null) {
      modules = ModuleManager.getInstance(project).getModules();
    }

    UnusedResourcesProcessor processor = new UnusedResourcesProcessor(project, modules, filter);
    if (skipIds) {
      processor.setIncludeIds(true);
    }

    if (skipDialog || ApplicationManager.getApplication().isUnitTestMode()) {
      processor.run();
    }
    else {
      UnusedResourcesDialog dialog = new UnusedResourcesDialog(project, processor);
      dialog.show();
    }
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, new PsiElement[]{file}, dataContext);
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    Set<Module> moduleSet = Sets.newHashSet();
    Module[] modules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modules != null) {
      Collections.addAll(moduleSet, modules);
    }
    else {
      Module module = LangDataKeys.MODULE.getData(dataContext);
      if (module != null) {
        moduleSet.add(module);
      }
    }
    for (PsiElement element : elements) {
      Module module = ModuleUtilCore.findModuleForPsiElement(element);
      if (module != null) {
        moduleSet.add(module);
      }
    }

    invoke(project, moduleSet.toArray(new Module[moduleSet.size()]), null, false, false);
  }
}

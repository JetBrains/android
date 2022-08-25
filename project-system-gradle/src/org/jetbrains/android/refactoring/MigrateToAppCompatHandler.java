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

import static java.util.Collections.emptySet;

import com.android.annotations.concurrency.UiThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

public class MigrateToAppCompatHandler implements RefactoringActionHandler {
  @Override
  @UiThread
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invoke(project, new PsiElement[]{file}, dataContext);
  }

  @Override
  @UiThread
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {

    MigrateToAppCompatProcessor processor;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      processor = new MigrateToAppCompatProcessor(project, (artifact, version) -> new AppCompatStyleMigration(emptySet(), emptySet()));
    }
    else {
      processor = new MigrateToAppCompatProcessor(project);
      processor.setPreviewUsages(true);
    }
    processor.run();
  }
}

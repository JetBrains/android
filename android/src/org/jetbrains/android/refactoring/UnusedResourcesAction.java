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

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import org.jetbrains.annotations.NotNull;

/**
 * Deletes unused resources, if any.
 * <p>
 * Possible improvements:
 * <ul>
 * <li> If resource declarations are preceded by comments, remove those too? </li>
 * <li> Do textual scans of source sets for other variants to make sure this doesn't
 * remove unused resources referenced in other variants</li>
 * <li> Unused resources corresponding to Gradle model resValues don't have corresponding
 * source locations, so these are currently not removed.</li>
 * </ul>
 */
public class UnusedResourcesAction extends BaseRefactoringAction {
  public UnusedResourcesAction() {
  }

  @Override
  protected boolean isEnabledOnDataContext(DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  @Override
  public boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    return true;
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new UnusedResourcesHandler();
  }
}

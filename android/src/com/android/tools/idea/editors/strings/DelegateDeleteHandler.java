/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.intellij.ide.util.DeleteHandler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.safeDelete.SafeDeleteDialog;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The best of both worlds!
 * like {@link com.intellij.ide.util.DeleteHandler} in that it lets you pick both safe and normal delete.
 * like {@link com.intellij.refactoring.safeDelete.SafeDeleteHandler} in that it checks Delegates so it find usages correctly.
 */
public class DelegateDeleteHandler {
  private DelegateDeleteHandler() {
  }

  /**
   * @see com.intellij.refactoring.safeDelete.SafeDeleteHandler#invoke(Project, PsiElement[], boolean)
   */
  public static void deletePsiElement(@NotNull Project project, @NotNull PsiElement[] elementsToDelete, @Nullable Module module) {
    if (elementsToDelete.length == 0) {
      return;
    }

    // Code taken from SafeDeleteHandler.invoke, that runs to checkDelegates
    // without this code, DeleteHandler fails to find usages even in safe delete mode.
    // we cant do this and then call DeleteHandler.deletePsiElement as the filterAncestors
    // at the start of that method will lose all the search elements.
    // and we cant call DeleteHandler with only search elements as then it will fail to delete the string fully
    final PsiElement[] elementRoots = PsiTreeUtil.filterAncestors(elementsToDelete);
    Set<PsiElement> elementsSet = new HashSet<>(Arrays.asList(elementRoots));
    Set<PsiElement> fullElementsSet = new LinkedHashSet<>();
    for (PsiElement element : elementRoots) {
      boolean found = false;
      for (SafeDeleteProcessorDelegate delegate : Extensions.getExtensions(SafeDeleteProcessorDelegate.EP_NAME)) {
        if (delegate.handlesElement(element)) {
          found = true;
          Collection<? extends PsiElement> addElements = delegate instanceof SafeDeleteProcessorDelegateBase
                                                         ? ((SafeDeleteProcessorDelegateBase)delegate)
                                                           .getElementsToSearch(element, module, elementsSet)
                                                         : delegate.getElementsToSearch(element, elementsSet);
          if (addElements == null) {
            return;
          }
          fullElementsSet.addAll(addElements);
          break;
        }
      }
      if (!found) {
        fullElementsSet.add(element);
      }
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, fullElementsSet, true)) return;
    PsiElement[] elements = PsiUtilCore.toPsiElementArray(fullElementsSet);

    boolean safeDeleteApplicable = true;
    for (int i = 0; i < elements.length && safeDeleteApplicable; i++) {
      PsiElement element = elements[i];
      safeDeleteApplicable = SafeDeleteProcessor.validElement(element);
    }

    final boolean dumb = DumbService.getInstance(project).isDumb();
    if (!safeDeleteApplicable || dumb) {
      DeleteHandler.deletePsiElement(elementRoots, project, true);
      return;
    }

    final Ref<Boolean> exit = Ref.create(false);
    final SafeDeleteDialog dialog = new SafeDeleteDialog(project, elements, dialog1 -> {
      if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(elements), true)) return;

      SafeDeleteProcessor processor = SafeDeleteProcessor.createInstance(project, () -> {
        exit.set(true);
        dialog1.close(DialogWrapper.OK_EXIT_CODE);
      }, elements, dialog1.isSearchInComments(), dialog1.isSearchForTextOccurences(), true);

      processor.run();
    }) {
      @Override
      protected boolean isDelete() {
        // this lets the dialog have the option to do both safe and normal delete
        return true;
      }
    };
    dialog.setTitle(RefactoringBundle.message("delete.title"));
    if (!dialog.showAndGet() || exit.get()) {
      // either dialog was cancelled or safe delete has finished
      return;
    }

    // doing non safe delete
    DeleteHandler.deletePsiElement(elementRoots, project, false);
  }
}

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

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import org.jetbrains.annotations.NotNull;

class UnusedResourcesUsageViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiElement[] myElements;

  UnusedResourcesUsageViewDescriptor(PsiElement[] elements) {
    myElements = elements;
  }

  @Override
  @NotNull
  public PsiElement[] getElements() {
    return myElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("items.to.be.deleted");
  }

  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return String.format("Unused Resource Declarations (%1$d resources in %2$d files)", usagesCount, filesCount);
  }
}

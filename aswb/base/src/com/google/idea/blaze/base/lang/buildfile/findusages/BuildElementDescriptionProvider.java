/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.findusages;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.usageView.UsageViewLongNameLocation;
import com.intellij.usageView.UsageViewShortNameLocation;
import javax.annotation.Nullable;

/** Controls text shown for target in the 'find usages' dialog. */
public class BuildElementDescriptionProvider implements ElementDescriptionProvider {
  @Nullable
  @Override
  public String getElementDescription(PsiElement element, ElementDescriptionLocation location) {
    if (!(element instanceof BuildElement)) {
      return null;
    }
    if (location instanceof UsageViewLongNameLocation) {
      return ((BuildElement) element).getPresentableText();
    }
    if (location instanceof UsageViewShortNameLocation) {
      if (element instanceof PsiNameIdentifierOwner) {
        // this is used by rename operations, so needs to be accurate
        return ((PsiNameIdentifierOwner) element).getName();
      }
      if (element instanceof PsiFile) {
        return ((PsiFile) element).getName();
      }
      return ((BuildElement) element).getPresentableText();
    }
    return null;
  }
}

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
package com.google.idea.blaze.java.psi;

import com.google.idea.blaze.base.lang.buildfile.search.PsiFileProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import javax.annotation.Nullable;

/** Replaces top-level java classes with their corresponding PsiFile */
public class JavaPsiFileProvider implements PsiFileProvider {

  @Nullable
  @Override
  public PsiFile asFileSearch(PsiElement elementToSearch) {
    if (elementToSearch instanceof PsiClass) {
      elementToSearch = elementToSearch.getParent();
    }
    return elementToSearch instanceof PsiFile ? (PsiFile) elementToSearch : null;
  }
}

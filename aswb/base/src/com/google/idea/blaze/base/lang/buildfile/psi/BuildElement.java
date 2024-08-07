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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;

/** Base class for all BUILD file PSI elements */
public interface BuildElement extends NavigatablePsiElement {

  @Nullable
  static BuildElement asBuildElement(PsiElement psiElement) {
    return psiElement instanceof BuildElement ? (BuildElement) psiElement : null;
  }

  Statement[] EMPTY_ARRAY = new Statement[0];

  String getPresentableText();

  /** See {@link com.intellij.navigation.ItemPresentation#getLocationString}. */
  @Nullable
  default String getLocationString() {
    return null;
  }

  @Nullable
  PsiElement getReferencedElement();

  <P extends PsiElement> P[] childrenOfClass(Class<P> psiClass);

  @Nullable
  <P extends PsiElement> P firstChildOfClass(Class<P> psiClass);

  @Nullable
  BlazePackage getBlazePackage();
}

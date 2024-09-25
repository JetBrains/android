/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.resolve.provider;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import javax.annotation.Nullable;

/** Provides a psi element, given a {@link PsiManager}. */
interface PsiElementProvider {

  @Nullable
  PsiElement get(PsiManager manager);

  static PsiElementProvider getParent(PsiElementProvider provider) {
    return (manager) -> {
      PsiElement psi = provider.get(manager);
      return psi != null ? psi.getParent() : null;
    };
  }
}

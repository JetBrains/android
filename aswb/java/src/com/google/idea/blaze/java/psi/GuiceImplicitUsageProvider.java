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
package com.google.idea.blaze.java.psi;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;

/** Suppresses spurious 'unused' warnings in code using Guice dependency injection. */
class GuiceImplicitUsageProvider implements ImplicitUsageProvider {

  private static final ImmutableSet<String> IMPLICIT_METHOD_USAGE_ANNOTATIONS =
      ImmutableSet.of(
          "com.google.inject.Provides",
          "com.google.inject.multibindings.ProvidesIntoSet",
          "com.google.inject.multibindings.ProvidesIntoMap");

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    if (!(element instanceof PsiMethod)) {
      return false;
    }
    PsiMethod method = (PsiMethod) element;
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    PsiModifierList modifiers = method.getModifierList();
    return IMPLICIT_METHOD_USAGE_ANNOTATIONS
        .stream()
        .anyMatch(s -> modifiers.findAnnotation(s) != null);
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return false;
  }
}

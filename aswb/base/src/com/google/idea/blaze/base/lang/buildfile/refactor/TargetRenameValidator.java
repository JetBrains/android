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
package com.google.idea.blaze.base.lang.buildfile.refactor;

import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;

/** Overrides name validation for target names. */
public class TargetRenameValidator implements RenameInputValidator {

  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    // FuncallExpression is the owner of the target name.
    return PlatformPatterns.psiElement(FuncallExpression.class);
  }

  @Override
  public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
    return TargetName.validate(newName) == null;
  }
}

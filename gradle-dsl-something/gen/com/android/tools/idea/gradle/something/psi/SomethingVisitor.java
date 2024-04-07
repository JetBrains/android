/*
 * Copyright (C) 2024 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from something.bnf. Do not edit it manually.
package com.android.tools.idea.gradle.something.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNamedElement;

public class SomethingVisitor extends PsiElementVisitor {

  public void visitArgumentsList(@NotNull SomethingArgumentsList o) {
    visitElement(o);
  }

  public void visitAssignment(@NotNull SomethingAssignment o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
  }

  public void visitBare(@NotNull SomethingBare o) {
    visitProperty(o);
  }

  public void visitBlock(@NotNull SomethingBlock o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
  }

  public void visitFactory(@NotNull SomethingFactory o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
    // visitValue(o);
  }

  public void visitIdentifier(@NotNull SomethingIdentifier o) {
    visitPsiNamedElement(o);
  }

  public void visitLiteral(@NotNull SomethingLiteral o) {
    visitValue(o);
  }

  public void visitProperty(@NotNull SomethingProperty o) {
    visitValue(o);
  }

  public void visitQualified(@NotNull SomethingQualified o) {
    visitProperty(o);
  }

  public void visitEntry(@NotNull SomethingEntry o) {
    visitElement(o);
  }

  public void visitValue(@NotNull SomethingValue o) {
    visitElement(o);
  }

  public void visitPsiNamedElement(@NotNull PsiNamedElement o) {
    visitElement(o);
  }

  public void visitElement(@NotNull SomethingElement o) {
    super.visitElement(o);
  }

}

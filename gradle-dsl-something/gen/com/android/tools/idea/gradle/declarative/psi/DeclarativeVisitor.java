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

// ATTENTION: This file has been automatically generated from declarative.bnf. Do not edit it manually.
package com.android.tools.idea.gradle.declarative.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.ContributedReferenceHost;

public class DeclarativeVisitor extends PsiElementVisitor {

  public void visitArgumentsList(@NotNull DeclarativeArgumentsList o) {
    visitElement(o);
  }

  public void visitAssignment(@NotNull DeclarativeAssignment o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
  }

  public void visitBare(@NotNull DeclarativeBare o) {
    visitProperty(o);
  }

  public void visitBlock(@NotNull DeclarativeBlock o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
  }

  public void visitBlockGroup(@NotNull DeclarativeBlockGroup o) {
    visitElement(o);
  }

  public void visitFactory(@NotNull DeclarativeFactory o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
    // visitValue(o);
  }

  public void visitIdentifier(@NotNull DeclarativeIdentifier o) {
    visitPsiNamedElement(o);
    // visitNavigatablePsiElement(o);
  }

  public void visitLiteral(@NotNull DeclarativeLiteral o) {
    visitValue(o);
  }

  public void visitProperty(@NotNull DeclarativeProperty o) {
    visitValue(o);
    // visitContributedReferenceHost(o);
  }

  public void visitQualified(@NotNull DeclarativeQualified o) {
    visitProperty(o);
  }

  public void visitEntry(@NotNull DeclarativeEntry o) {
    visitElement(o);
  }

  public void visitValue(@NotNull DeclarativeValue o) {
    visitElement(o);
  }

  public void visitPsiNamedElement(@NotNull PsiNamedElement o) {
    visitElement(o);
  }

  public void visitElement(@NotNull DeclarativeElement o) {
    super.visitElement(o);
  }

}

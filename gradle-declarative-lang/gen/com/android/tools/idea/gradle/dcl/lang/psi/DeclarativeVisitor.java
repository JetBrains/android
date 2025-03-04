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
package com.android.tools.idea.gradle.dcl.lang.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.ContributedReferenceHost;

public class DeclarativeVisitor extends PsiElementVisitor {

  public void visitArgument(@NotNull DeclarativeArgument o) {
    visitElement(o);
  }

  public void visitArgumentsList(@NotNull DeclarativeArgumentsList o) {
    visitElement(o);
  }

  public void visitAssignableBare(@NotNull DeclarativeAssignableBare o) {
    visitAssignableProperty(o);
  }

  public void visitAssignableProperty(@NotNull DeclarativeAssignableProperty o) {
    visitContributedReferenceHost(o);
    // visitReceiverPrefixed(o);
  }

  public void visitAssignableQualified(@NotNull DeclarativeAssignableQualified o) {
    visitAssignableProperty(o);
  }

  public void visitAssignment(@NotNull DeclarativeAssignment o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
  }

  public void visitBare(@NotNull DeclarativeBare o) {
    visitProperty(o);
  }

  public void visitBareReceiver(@NotNull DeclarativeBareReceiver o) {
    visitPropertyReceiver(o);
  }

  public void visitBlock(@NotNull DeclarativeBlock o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
  }

  public void visitBlockGroup(@NotNull DeclarativeBlockGroup o) {
    visitElement(o);
  }

  public void visitEmbeddedFactory(@NotNull DeclarativeEmbeddedFactory o) {
    visitAbstractFactory(o);
  }

  public void visitFactoryPropertyReceiver(@NotNull DeclarativeFactoryPropertyReceiver o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
    // visitReceiverBasedFactory(o);
  }

  public void visitFactoryReceiver(@NotNull DeclarativeFactoryReceiver o) {
    visitEntry(o);
    // visitIdentifierOwner(o);
    // visitReceiverBasedFactory(o);
  }

  public void visitIdentifier(@NotNull DeclarativeIdentifier o) {
    visitPsiNamedElement(o);
    // visitNavigatablePsiElement(o);
  }

  public void visitLiteral(@NotNull DeclarativeLiteral o) {
    visitSimpleLiteral(o);
    // visitContributedReferenceHost(o);
  }

  public void visitPair(@NotNull DeclarativePair o) {
    visitValue(o);
  }

  public void visitProperty(@NotNull DeclarativeProperty o) {
    visitValue(o);
    // visitContributedReferenceHost(o);
    // visitReceiverPrefixed(o);
    // visitValueFieldOwner(o);
  }

  public void visitPropertyReceiver(@NotNull DeclarativePropertyReceiver o) {
    visitReceiverPrefixed(o);
    // visitValueFieldOwner(o);
  }

  public void visitQualified(@NotNull DeclarativeQualified o) {
    visitProperty(o);
  }

  public void visitQualifiedReceiver(@NotNull DeclarativeQualifiedReceiver o) {
    visitPropertyReceiver(o);
  }

  public void visitReceiverPrefixedFactory(@NotNull DeclarativeReceiverPrefixedFactory o) {
    visitFactoryReceiver(o);
  }

  public void visitSimpleFactory(@NotNull DeclarativeSimpleFactory o) {
    visitFactoryReceiver(o);
  }

  public void visitSimpleLiteral(@NotNull DeclarativeSimpleLiteral o) {
    visitValue(o);
  }

  public void visitAbstractFactory(@NotNull DeclarativeAbstractFactory o) {
    visitElement(o);
  }

  public void visitEntry(@NotNull DeclarativeEntry o) {
    visitElement(o);
  }

  public void visitReceiverPrefixed(@NotNull DeclarativeReceiverPrefixed o) {
    visitElement(o);
  }

  public void visitValue(@NotNull DeclarativeValue o) {
    visitElement(o);
  }

  public void visitContributedReferenceHost(@NotNull ContributedReferenceHost o) {
    visitElement(o);
  }

  public void visitPsiNamedElement(@NotNull PsiNamedElement o) {
    visitElement(o);
  }

  public void visitElement(@NotNull DeclarativeElement o) {
    super.visitElement(o);
  }

}

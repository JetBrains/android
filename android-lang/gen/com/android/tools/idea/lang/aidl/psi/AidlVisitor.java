/*
 * Copyright (C) 2017 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from Aidl.bnf. Do not edit it manually.

package com.android.tools.idea.lang.aidl.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import  com.intellij.psi.PsiNameIdentifierOwner;

public class AidlVisitor extends PsiElementVisitor {

  public void visitBody(@NotNull AidlBody o) {
    visitPsiCompositeElement(o);
  }

  public void visitClassOrInterfaceType(@NotNull AidlClassOrInterfaceType o) {
    visitType(o);
  }

  public void visitDeclarationName(@NotNull AidlDeclarationName o) {
    visitNamedElement(o);
  }

  public void visitDirection(@NotNull AidlDirection o) {
    visitPsiCompositeElement(o);
  }

  public void visitHeaders(@NotNull AidlHeaders o) {
    visitPsiCompositeElement(o);
  }

  public void visitImportStatement(@NotNull AidlImportStatement o) {
    visitPsiCompositeElement(o);
  }

  public void visitInterfaceDeclaration(@NotNull AidlInterfaceDeclaration o) {
    visitDeclaration(o);
  }

  public void visitMethodDeclaration(@NotNull AidlMethodDeclaration o) {
    visitDeclaration(o);
  }

  public void visitNameComponent(@NotNull AidlNameComponent o) {
    visitNamedElement(o);
  }

  public void visitPackageStatement(@NotNull AidlPackageStatement o) {
    visitPsiCompositeElement(o);
  }

  public void visitParameter(@NotNull AidlParameter o) {
    visitPsiCompositeElement(o);
  }

  public void visitParcelableDeclaration(@NotNull AidlParcelableDeclaration o) {
    visitDeclaration(o);
  }

  public void visitPrimitiveType(@NotNull AidlPrimitiveType o) {
    visitType(o);
  }

  public void visitQualifiedName(@NotNull AidlQualifiedName o) {
    visitPsiCompositeElement(o);
  }

  public void visitType(@NotNull AidlType o) {
    visitPsiCompositeElement(o);
  }

  public void visitTypeArguments(@NotNull AidlTypeArguments o) {
    visitPsiCompositeElement(o);
  }

  public void visitDeclaration(@NotNull AidlDeclaration o) {
    visitPsiCompositeElement(o);
  }

  public void visitNamedElement(@NotNull AidlNamedElement o) {
    visitPsiCompositeElement(o);
  }

  public void visitPsiCompositeElement(@NotNull AidlPsiCompositeElement o) {
    visitElement(o);
  }

}

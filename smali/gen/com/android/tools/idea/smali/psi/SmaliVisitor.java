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

// Generated from Smali.bnf, do not modify
package com.android.tools.idea.smali.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class SmaliVisitor extends PsiElementVisitor {

  public void visitAccessModifier(@NotNull SmaliAccessModifier o) {
    visitPsiElement(o);
  }

  public void visitAnnotationProperty(@NotNull SmaliAnnotationProperty o) {
    visitPsiElement(o);
  }

  public void visitAnnotationsSpec(@NotNull SmaliAnnotationsSpec o) {
    visitPsiElement(o);
  }

  public void visitBool(@NotNull SmaliBool o) {
    visitPsiElement(o);
  }

  public void visitClassName(@NotNull SmaliClassName o) {
    visitJavaClassRef(o);
  }

  public void visitClassSpec(@NotNull SmaliClassSpec o) {
    visitPsiElement(o);
  }

  public void visitFieldName(@NotNull SmaliFieldName o) {
    visitPsiElement(o);
  }

  public void visitFieldSpec(@NotNull SmaliFieldSpec o) {
    visitPsiElement(o);
  }

  public void visitFieldValue(@NotNull SmaliFieldValue o) {
    visitPsiElement(o);
  }

  public void visitImplementsSpec(@NotNull SmaliImplementsSpec o) {
    visitPsiElement(o);
  }

  public void visitMethodBody(@NotNull SmaliMethodBody o) {
    visitPsiElement(o);
  }

  public void visitMethodSpec(@NotNull SmaliMethodSpec o) {
    visitPsiElement(o);
  }

  public void visitMethodStart(@NotNull SmaliMethodStart o) {
    visitPsiElement(o);
  }

  public void visitParameterDeclaration(@NotNull SmaliParameterDeclaration o) {
    visitPsiElement(o);
  }

  public void visitPrimitiveType(@NotNull SmaliPrimitiveType o) {
    visitPsiElement(o);
  }

  public void visitPropertyValue(@NotNull SmaliPropertyValue o) {
    visitPsiElement(o);
  }

  public void visitRegularMethodStart(@NotNull SmaliRegularMethodStart o) {
    visitPsiElement(o);
  }

  public void visitReturnType(@NotNull SmaliReturnType o) {
    visitPsiElement(o);
  }

  public void visitSingleValue(@NotNull SmaliSingleValue o) {
    visitPsiElement(o);
  }

  public void visitSingleValues(@NotNull SmaliSingleValues o) {
    visitPsiElement(o);
  }

  public void visitSourceSpec(@NotNull SmaliSourceSpec o) {
    visitPsiElement(o);
  }

  public void visitSuperSpec(@NotNull SmaliSuperSpec o) {
    visitPsiElement(o);
  }

  public void visitValueArray(@NotNull SmaliValueArray o) {
    visitPsiElement(o);
  }

  public void visitVoidType(@NotNull SmaliVoidType o) {
    visitPsiElement(o);
  }

  public void visitJavaClassRef(@NotNull JavaClassRef o) {
    visitElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}

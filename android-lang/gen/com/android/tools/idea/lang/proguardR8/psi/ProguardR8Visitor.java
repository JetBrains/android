/*
 * Copyright (C) 2019 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from proguardR8.bnf. Do not edit it manually.

package com.android.tools.idea.lang.proguardR8.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class ProguardR8Visitor extends PsiElementVisitor {

  public void visitAnnotationName(@NotNull ProguardR8AnnotationName o) {
    visitPsiElement(o);
  }

  public void visitAnyFieldOrMethod(@NotNull ProguardR8AnyFieldOrMethod o) {
    visitPsiElement(o);
  }

  public void visitAnyNotPrimitiveType(@NotNull ProguardR8AnyNotPrimitiveType o) {
    visitPsiElement(o);
  }

  public void visitAnyPrimitiveType(@NotNull ProguardR8AnyPrimitiveType o) {
    visitPsiElement(o);
  }

  public void visitAnyType(@NotNull ProguardR8AnyType o) {
    visitPsiElement(o);
  }

  public void visitArrayType(@NotNull ProguardR8ArrayType o) {
    visitPsiElement(o);
  }

  public void visitClassMemberName(@NotNull ProguardR8ClassMemberName o) {
    visitPsiElement(o);
  }

  public void visitClassModifier(@NotNull ProguardR8ClassModifier o) {
    visitPsiElement(o);
  }

  public void visitClassName(@NotNull ProguardR8ClassName o) {
    visitPsiElement(o);
  }

  public void visitClassSpecificationBody(@NotNull ProguardR8ClassSpecificationBody o) {
    visitPsiElement(o);
  }

  public void visitClassSpecificationHeader(@NotNull ProguardR8ClassSpecificationHeader o) {
    visitPsiElement(o);
  }

  public void visitClassType(@NotNull ProguardR8ClassType o) {
    visitPsiElement(o);
  }

  public void visitConstructorName(@NotNull ProguardR8ConstructorName o) {
    visitClassMemberName(o);
  }

  public void visitField(@NotNull ProguardR8Field o) {
    visitClassMember(o);
  }

  public void visitFieldsSpecification(@NotNull ProguardR8FieldsSpecification o) {
    visitPsiElement(o);
  }

  public void visitFile(@NotNull ProguardR8File o) {
    visitPsiElement(o);
  }

  public void visitFileFilter(@NotNull ProguardR8FileFilter o) {
    visitPsiElement(o);
  }

  public void visitFlag(@NotNull ProguardR8Flag o) {
    visitPsiElement(o);
  }

  public void visitFlagArgument(@NotNull ProguardR8FlagArgument o) {
    visitPsiElement(o);
  }

  public void visitFullyQualifiedNameConstructor(@NotNull ProguardR8FullyQualifiedNameConstructor o) {
    visitClassMember(o);
  }

  public void visitIncludeFile(@NotNull ProguardR8IncludeFile o) {
    visitPsiElement(o);
  }

  public void visitJavaPrimitive(@NotNull ProguardR8JavaPrimitive o) {
    visitPsiElement(o);
  }

  public void visitJavaRule(@NotNull ProguardR8JavaRule o) {
    visitPsiElement(o);
  }

  public void visitKeepOptionModifier(@NotNull ProguardR8KeepOptionModifier o) {
    visitPsiElement(o);
  }

  public void visitMethod(@NotNull ProguardR8Method o) {
    visitClassMember(o);
  }

  public void visitMethodSpecification(@NotNull ProguardR8MethodSpecification o) {
    visitPsiElement(o);
  }

  public void visitModifier(@NotNull ProguardR8Modifier o) {
    visitPsiElement(o);
  }

  public void visitParameters(@NotNull ProguardR8Parameters o) {
    visitPsiElement(o);
  }

  public void visitQualifiedName(@NotNull ProguardR8QualifiedName o) {
    visitPsiElement(o);
  }

  public void visitRule(@NotNull ProguardR8Rule o) {
    visitPsiElement(o);
  }

  public void visitRuleWithClassFilter(@NotNull ProguardR8RuleWithClassFilter o) {
    visitPsiElement(o);
  }

  public void visitRuleWithClassSpecification(@NotNull ProguardR8RuleWithClassSpecification o) {
    visitPsiElement(o);
  }

  public void visitSuperClassName(@NotNull ProguardR8SuperClassName o) {
    visitPsiElement(o);
  }

  public void visitType(@NotNull ProguardR8Type o) {
    visitPsiElement(o);
  }

  public void visitTypeList(@NotNull ProguardR8TypeList o) {
    visitPsiElement(o);
  }

  public void visitClassMember(@NotNull ProguardR8ClassMember o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}

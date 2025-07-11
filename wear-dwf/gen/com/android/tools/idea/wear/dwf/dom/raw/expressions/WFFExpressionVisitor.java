/*
 * Copyright (C) 2025 The Android Open Source Project
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

// ATTENTION: This file has been automatically generated from
// wear-dwf/src/com/android/tools/idea/wear/dwf/dom/raw/expressions/wff_expressions.bnf
// Do not edit it manually.
package com.android.tools.idea.wear.dwf.dom.raw.expressions;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class WFFExpressionVisitor extends PsiElementVisitor {

  public void visitAndExpr(@NotNull WFFExpressionAndExpr o) {
    visitExpr(o);
  }

  public void visitArgList(@NotNull WFFExpressionArgList o) {
    visitPsiElement(o);
  }

  public void visitBitComplExpr(@NotNull WFFExpressionBitComplExpr o) {
    visitExpr(o);
  }

  public void visitCallExpr(@NotNull WFFExpressionCallExpr o) {
    visitExpr(o);
  }

  public void visitColorIndex(@NotNull WFFExpressionColorIndex o) {
    visitPsiElement(o);
  }

  public void visitConditionalExpr(@NotNull WFFExpressionConditionalExpr o) {
    visitExpr(o);
  }

  public void visitConditionalOp(@NotNull WFFExpressionConditionalOp o) {
    visitPsiElement(o);
  }

  public void visitConfiguration(@NotNull WFFExpressionConfiguration o) {
    visitPsiElement(o);
  }

  public void visitConfigurationId(@NotNull WFFExpressionConfigurationId o) {
    visitPsiElement(o);
  }

  public void visitDataSource(@NotNull WFFExpressionDataSource o) {
    visitPsiElement(o);
  }

  public void visitDivExpr(@NotNull WFFExpressionDivExpr o) {
    visitExpr(o);
  }

  public void visitElvisExpr(@NotNull WFFExpressionElvisExpr o) {
    visitExpr(o);
  }

  public void visitExpr(@NotNull WFFExpressionExpr o) {
    visitPsiElement(o);
  }

  public void visitFunctionId(@NotNull WFFExpressionFunctionId o) {
    visitPsiElement(o);
  }

  public void visitLiteralExpr(@NotNull WFFExpressionLiteralExpr o) {
    visitExpr(o);
  }

  public void visitMinusExpr(@NotNull WFFExpressionMinusExpr o) {
    visitExpr(o);
  }

  public void visitModExpr(@NotNull WFFExpressionModExpr o) {
    visitExpr(o);
  }

  public void visitMulExpr(@NotNull WFFExpressionMulExpr o) {
    visitExpr(o);
  }

  public void visitNumber(@NotNull WFFExpressionNumber o) {
    visitPsiElement(o);
  }

  public void visitOrExpr(@NotNull WFFExpressionOrExpr o) {
    visitExpr(o);
  }

  public void visitParenExpr(@NotNull WFFExpressionParenExpr o) {
    visitExpr(o);
  }

  public void visitPlusExpr(@NotNull WFFExpressionPlusExpr o) {
    visitExpr(o);
  }

  public void visitUnaryMinExpr(@NotNull WFFExpressionUnaryMinExpr o) {
    visitExpr(o);
  }

  public void visitUnaryNotExpr(@NotNull WFFExpressionUnaryNotExpr o) {
    visitExpr(o);
  }

  public void visitUnaryPlusExpr(@NotNull WFFExpressionUnaryPlusExpr o) {
    visitExpr(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}

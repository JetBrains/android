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

// ATTENTION: This file has been automatically generated from db.bnf. Do not edit it manually.

package com.android.tools.idea.lang.databinding.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class PsiDbVisitor extends PsiElementVisitor {

  public void visitAddExpr(@NotNull PsiDbAddExpr o) {
    visitExpr(o);
  }

  public void visitBinaryAndExpr(@NotNull PsiDbBinaryAndExpr o) {
    visitExpr(o);
  }

  public void visitBinaryOrExpr(@NotNull PsiDbBinaryOrExpr o) {
    visitExpr(o);
  }

  public void visitBinaryXorExpr(@NotNull PsiDbBinaryXorExpr o) {
    visitExpr(o);
  }

  public void visitBitShiftExpr(@NotNull PsiDbBitShiftExpr o) {
    visitExpr(o);
  }

  public void visitBracketExpr(@NotNull PsiDbBracketExpr o) {
    visitExpr(o);
  }

  public void visitCallExpr(@NotNull PsiDbCallExpr o) {
    visitExpr(o);
  }

  public void visitCastExpr(@NotNull PsiDbCastExpr o) {
    visitExpr(o);
  }

  public void visitClassExtractionExpr(@NotNull PsiDbClassExtractionExpr o) {
    visitExpr(o);
  }

  public void visitClassOrInterfaceType(@NotNull PsiDbClassOrInterfaceType o) {
    visitPsiElement(o);
  }

  public void visitConstantValue(@NotNull PsiDbConstantValue o) {
    visitPsiElement(o);
  }

  public void visitDefaults(@NotNull PsiDbDefaults o) {
    visitPsiElement(o);
  }

  public void visitEqComparisonExpr(@NotNull PsiDbEqComparisonExpr o) {
    visitExpr(o);
  }

  public void visitExpr(@NotNull PsiDbExpr o) {
    visitPsiElement(o);
  }

  public void visitExpressionList(@NotNull PsiDbExpressionList o) {
    visitPsiElement(o);
  }

  public void visitFunctionRefExpr(@NotNull PsiDbFunctionRefExpr o) {
    visitExpr(o);
  }

  public void visitId(@NotNull PsiDbId o) {
    visitPsiElement(o);
  }

  public void visitIneqComparisonExpr(@NotNull PsiDbIneqComparisonExpr o) {
    visitExpr(o);
  }

  public void visitInferredFormalParameterList(@NotNull PsiDbInferredFormalParameterList o) {
    visitPsiElement(o);
  }

  public void visitInstanceOfExpr(@NotNull PsiDbInstanceOfExpr o) {
    visitExpr(o);
  }

  public void visitLambdaExpression(@NotNull PsiDbLambdaExpression o) {
    visitPsiElement(o);
  }

  public void visitLambdaParameters(@NotNull PsiDbLambdaParameters o) {
    visitPsiElement(o);
  }

  public void visitLiteralExpr(@NotNull PsiDbLiteralExpr o) {
    visitExpr(o);
  }

  public void visitLogicalAndExpr(@NotNull PsiDbLogicalAndExpr o) {
    visitExpr(o);
  }

  public void visitLogicalOrExpr(@NotNull PsiDbLogicalOrExpr o) {
    visitExpr(o);
  }

  public void visitMulExpr(@NotNull PsiDbMulExpr o) {
    visitExpr(o);
  }

  public void visitNegationExpr(@NotNull PsiDbNegationExpr o) {
    visitExpr(o);
  }

  public void visitNullCoalesceExpr(@NotNull PsiDbNullCoalesceExpr o) {
    visitExpr(o);
  }

  public void visitParenExpr(@NotNull PsiDbParenExpr o) {
    visitExpr(o);
  }

  public void visitPrimitiveType(@NotNull PsiDbPrimitiveType o) {
    visitPsiElement(o);
  }

  public void visitRefExpr(@NotNull PsiDbRefExpr o) {
    visitExpr(o);
  }

  public void visitResourceParameters(@NotNull PsiDbResourceParameters o) {
    visitPsiElement(o);
  }

  public void visitResourcesExpr(@NotNull PsiDbResourcesExpr o) {
    visitExpr(o);
  }

  public void visitSignChangeExpr(@NotNull PsiDbSignChangeExpr o) {
    visitExpr(o);
  }

  public void visitTernaryExpr(@NotNull PsiDbTernaryExpr o) {
    visitExpr(o);
  }

  public void visitType(@NotNull PsiDbType o) {
    visitPsiElement(o);
  }

  public void visitTypeArguments(@NotNull PsiDbTypeArguments o) {
    visitPsiElement(o);
  }

  public void visitVoidExpr(@NotNull PsiDbVoidExpr o) {
    visitExpr(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}

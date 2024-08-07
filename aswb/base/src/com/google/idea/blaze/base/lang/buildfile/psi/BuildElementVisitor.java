/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.intellij.psi.PsiElementVisitor;

/** Visitor for BUILD file PSI nodes */
public class BuildElementVisitor extends PsiElementVisitor {

  public void visitAssignmentStatement(AssignmentStatement node) {
    visitElement(node);
  }

  public void visitAugmentedAssignmentStatement(AugmentedAssignmentStatement node) {
    visitElement(node);
  }

  public void visitReturnStatement(ReturnStatement node) {
    visitElement(node);
  }

  public void visitArgument(Argument node) {
    visitElement(node);
  }

  public void visitKeywordArgument(Argument.Keyword node) {
    visitElement(node);
  }

  public void visitParameter(Parameter node) {
    visitElement(node);
  }

  public void visitLoadStatement(LoadStatement node) {
    visitElement(node);
  }

  public void visitIfStatement(IfStatement node) {
    visitElement(node);
  }

  public void visitIfPart(IfPart node) {
    visitElement(node);
  }

  public void visitElsePart(ElsePart node) {
    visitElement(node);
  }

  public void visitElseIfPart(ElseIfPart node) {
    visitElement(node);
  }

  public void visitFunctionStatement(FunctionStatement node) {
    visitElement(node);
  }

  public void visitFuncallExpression(FuncallExpression node) {
    visitElement(node);
  }

  public void visitForStatement(ForStatement node) {
    visitElement(node);
  }

  public void visitFlowStatement(FlowStatement node) {
    visitElement(node);
  }

  public void visitDotExpression(DotExpression node) {
    visitElement(node);
  }

  public void visitDictionaryLiteral(DictionaryLiteral node) {
    visitElement(node);
  }

  public void visitDictionaryEntryLiteral(DictionaryEntryLiteral node) {
    visitElement(node);
  }

  public void visitBinaryOpExpression(BinaryOpExpression node) {
    visitElement(node);
  }

  public void visitStringLiteral(StringLiteral node) {
    visitElement(node);
  }

  public void visitIntegerLiteral(IntegerLiteral node) {
    visitElement(node);
  }

  public void visitListLiteral(ListLiteral node) {
    visitElement(node);
  }

  public void visitStatementList(StatementList node) {
    visitElement(node);
  }

  public void visitFuncallArgList(ArgumentList node) {
    visitElement(node);
  }

  public void visitReferenceExpression(ReferenceExpression node) {
    visitElement(node);
  }

  public void visitTargetExpression(TargetExpression node) {
    visitElement(node);
  }

  public void visitListComprehensionSuffix(ListComprehensionExpression node) {
    visitElement(node);
  }

  public void visitFunctionParameterList(ParameterList node) {
    visitElement(node);
  }

  public void visitGlobExpression(GlobExpression node) {
    visitElement(node);
  }

  public void visitPassStatement(PassStatement node) {
    visitElement(node);
  }

  public void visitLoadedSymbol(LoadedSymbol node) {
    visitElement(node);
  }

  public void visitParenthesizedExpression(ParenthesizedExpression node) {
    visitElement(node);
  }

  public void visitTupleExpression(TupleExpression node) {
    visitElement(node);
  }
}

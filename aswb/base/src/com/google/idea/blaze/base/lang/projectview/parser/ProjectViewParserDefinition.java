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
package com.google.idea.blaze.base.lang.projectview.parser;

import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewLexer;
import com.google.idea.blaze.base.lang.projectview.lexer.ProjectViewTokenType;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewElementType;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewElementTypes;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiFile;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

/** Defines the project view file parser */
public class ProjectViewParserDefinition implements ParserDefinition {

  @Override
  public Lexer createLexer(Project project) {
    return new ProjectViewLexer();
  }

  @Override
  public PsiParser createParser(Project project) {
    return (root, builder) -> {
      PsiBuilder.Marker rootMarker = builder.mark();
      new ProjectViewPsiParser(builder).parseFile();
      rootMarker.done(root);
      return builder.getTreeBuilt();
    };
  }

  @Override
  public IFileElementType getFileNodeType() {
    return ProjectViewElementTypes.FILE;
  }

  @Override
  public TokenSet getWhitespaceTokens() {
    return TokenSet.create(ProjectViewTokenType.WHITESPACE);
  }

  @Override
  public TokenSet getCommentTokens() {
    return TokenSet.create(ProjectViewTokenType.COMMENT);
  }

  @Override
  public TokenSet getStringLiteralElements() {
    return TokenSet.EMPTY;
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    IElementType type = node.getElementType();
    if (type instanceof ProjectViewElementType) {
      return ((ProjectViewElementType) type).createElement(node);
    }
    return new ASTWrapperPsiElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new ProjectViewPsiFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return null;
  }
}

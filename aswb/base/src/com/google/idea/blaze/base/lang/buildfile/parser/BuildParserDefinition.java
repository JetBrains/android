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
package com.google.idea.blaze.base.lang.buildfile.parser;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexer;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexerBase;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementType;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementTypes;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.common.experiments.DeveloperFlag;
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
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;

/** Defines the BUILD file parser */
public class BuildParserDefinition implements ParserDefinition {

  private static final DeveloperFlag debug = new DeveloperFlag("build.file.debug.mode");

  @Override
  public Lexer createLexer(Project project) {
    return new BuildLexer(BuildLexerBase.LexerMode.Parsing);
  }

  @Override
  public PsiParser createParser(Project project) {
    return new BuildParser();
  }

  @Override
  public IFileElementType getFileNodeType() {
    return BuildElementTypes.BUILD_FILE;
  }

  @Override
  public TokenSet getWhitespaceTokens() {
    return convert(TokenKind.WHITESPACE, TokenKind.ILLEGAL);
  }

  @Override
  public TokenSet getCommentTokens() {
    return convert(TokenKind.COMMENT);
  }

  @Override
  public TokenSet getStringLiteralElements() {
    return convert(TokenKind.STRING);
  }

  @Override
  public PsiElement createElement(ASTNode node) {
    IElementType type = node.getElementType();
    if (type instanceof BuildElementType) {
      return ((BuildElementType) type).createElement(node);
    }
    return new ASTWrapperPsiElement(node);
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new BuildFile(viewProvider);
  }

  @Override
  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
    return SpaceRequirements.MAY;
  }

  private static TokenSet convert(TokenKind... blazeTokens) {
    return TokenSet.create(
        Lists.newArrayList(blazeTokens)
            .stream()
            .map(BuildToken::fromKind)
            .toArray(IElementType[]::new));
  }

  private static class BuildParser implements PsiParser {
    @Override
    public ASTNode parse(IElementType root, PsiBuilder builder) {
      if (debug.getValue()) {
        System.err.println(builder.getUserDataUnprotected(FileContextUtil.CONTAINING_FILE_KEY));
      }
      PsiBuilder.Marker rootMarker = builder.mark();
      ParsingContext context = new ParsingContext(builder);
      context.statementParser.parseFileInput();
      rootMarker.done(root);
      return builder.getTreeBuilt();
    }
  }
}

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
package com.google.idea.blaze.base.lang.buildfile.psi.util;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.Statement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.testFramework.LightVirtualFile;
import javax.annotation.Nullable;

/** Creates dummy BuildElements, e.g. for renaming purposes. */
public class BuildElementGenerator {

  public static BuildElementGenerator getInstance(Project project) {
    return project.getService(BuildElementGenerator.class);
  }

  private static final String DUMMY_FILENAME = "dummy.bzl";

  private final Project project;

  public BuildElementGenerator(Project project) {
    this.project = project;
  }

  public PsiFile createDummyFile(String contents) {
    PsiFileFactory factory = PsiFileFactory.getInstance(project);
    LightVirtualFile virtualFile =
        new LightVirtualFile(DUMMY_FILENAME, BuildFileType.INSTANCE, contents);
    PsiFile psiFile =
        ((PsiFileFactoryImpl) factory)
            .trySetupPsiForFile(virtualFile, BuildFileLanguage.INSTANCE, false, true);
    assert psiFile != null;
    return psiFile;
  }

  public ASTNode createNameIdentifier(String name) {
    PsiFile dummyFile = createDummyFile(name);
    ASTNode referenceNode = dummyFile.getNode().getFirstChildNode();
    ASTNode nameNode = referenceNode.getFirstChildNode();
    if (nameNode.getElementType() != BuildToken.IDENTIFIER) {
      throw new RuntimeException(
          "Expecting an IDENTIFIER node directly below the BuildFile PSI element");
    }
    return nameNode;
  }

  public ASTNode createStringNode(String contents) {
    PsiFile dummyFile = createDummyFile('"' + contents + '"');
    ASTNode literalNode = dummyFile.getNode().getFirstChildNode();
    ASTNode stringNode = literalNode.getFirstChildNode();
    assert (stringNode.getElementType() == BuildToken.fromKind(TokenKind.STRING));
    return stringNode;
  }

  public Argument.Keyword createKeywordArgument(String keyword, String value) {
    String dummyText = String.format("foo(%s = \"%s\")", keyword, value);
    FuncallExpression funcall = (FuncallExpression) createExpressionFromText(dummyText);
    return (Argument.Keyword) funcall.getArguments()[0];
  }

  public Expression createExpressionFromText(String text) {
    PsiFile dummyFile = createDummyFile(text);
    PsiElement element = dummyFile.getFirstChild();
    if (element instanceof Expression) {
      return (Expression) element;
    }
    throw new RuntimeException("Could not parse text as expression: '" + text + "'");
  }

  /** Returns null if the text can't be parsed as a statement. */
  @Nullable
  public Statement createStatementFromText(String text) {
    PsiFile dummyFile = createDummyFile(text);
    PsiElement element = dummyFile.getFirstChild();
    if (element instanceof Statement) {
      return (Statement) element;
    }
    return null;
  }
}

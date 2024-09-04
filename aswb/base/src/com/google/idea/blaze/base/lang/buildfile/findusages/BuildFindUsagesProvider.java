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
package com.google.idea.blaze.base.lang.buildfile.findusages;

import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexer;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildLexerBase.LexerMode;
import com.google.idea.blaze.base.lang.buildfile.lexer.BuildToken;
import com.google.idea.blaze.base.lang.buildfile.lexer.TokenKind;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.intellij.lang.HelpID;
import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;

/**
 * Required for highlighting references (among other things we don't currently support). Currently
 * only used by the fallback 'DefaultFindUsagesHandlerFactory'.
 */
public class BuildFindUsagesProvider implements FindUsagesProvider {

  @Override
  public boolean canFindUsagesFor(PsiElement psiElement) {
    return psiElement instanceof FuncallExpression
        || psiElement instanceof PsiNamedElement
        || psiElement instanceof ReferenceExpression;
  }

  @Override
  public String getHelpId(PsiElement psiElement) {
    if (psiElement instanceof FunctionStatement) {
      return "reference.dialogs.findUsages.method";
    }
    if (psiElement instanceof TargetExpression
        || psiElement instanceof Parameter
        || psiElement instanceof ReferenceExpression) {
      return "reference.dialogs.findUsages.variable";
    }
    // typically build rules and imported Skylark functions, but also all other function calls
    return HelpID.FIND_OTHER_USAGES;
  }

  @Override
  public String getType(PsiElement element) {
    if (element instanceof FunctionStatement) {
      return "function";
    }
    if (element instanceof Parameter) {
      return "parameter";
    }
    if (element instanceof ReferenceExpression || element instanceof TargetExpression) {
      return "variable";
    }
    if (element instanceof Argument.Keyword) {
      return "keyword argument";
    }
    if (element instanceof FuncallExpression) {
      return "rule";
    }
    return "";
  }

  /** Controls text shown for target element in the 'find usages' dialog */
  @Override
  public String getDescriptiveName(PsiElement element) {
    if (element instanceof BuildElement) {
      return ((BuildElement) element).getPresentableText();
    }
    return element.toString();
  }

  @Override
  public String getNodeText(PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }

  @Override
  public WordsScanner getWordsScanner() {
    return new DefaultWordsScanner(
        new BuildLexer(LexerMode.SyntaxHighlighting),
        tokenSet(TokenKind.IDENTIFIER),
        tokenSet(TokenKind.COMMENT),
        tokenSet(TokenKind.STRING));
  }

  private static TokenSet tokenSet(TokenKind token) {
    return TokenSet.create(BuildToken.fromKind(token));
  }
}

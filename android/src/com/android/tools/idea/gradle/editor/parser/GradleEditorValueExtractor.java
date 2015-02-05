/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * Allows to fill {@link GradleEditorModelParseContext target context's} cached
 * {@link GradleEditorModelParseContext#addCachedValue(GradleEditorModelParseContext.Value) value} and/or
 * {@link GradleEditorModelParseContext#addCachedVariable(GradleEditorModelParseContext.Variable, TextRange) variable} by the data
 * from the target PSI element.
 */
public class GradleEditorValueExtractor extends GroovyRecursiveElementVisitor {

  @NotNull private final GradleEditorModelParseContext myContext;
  @NotNull private final PsiElementVisitor myVisitor;

  private boolean myInterestedInReferencesOnly;

  public GradleEditorValueExtractor(@NotNull GradleEditorModelParseContext context) {
    myContext = context;
    myVisitor = new GroovyPsiElementVisitor(this);
  }

  @Nullable
  public static Pair<String, TextRange> extractMethodName(@NotNull PsiElement methodCall) {
    GrReferenceExpression methodNameRef = PsiTreeUtil.findChildOfType(methodCall, GrReferenceExpression.class);
    if (methodNameRef == null || methodNameRef.getParent() != methodCall) {
      return null;
    }
    String methodName = methodNameRef.getReferenceName();
    if (methodName == null) {
      return null;
    }
    return Pair.create(methodName, methodNameRef.getTextRange());
  }

  public void extractValueOrVariable(@NotNull PsiElement element) {
    element.accept(myVisitor);
  }

  @Override
  public void visitLiteralExpression(GrLiteral literal) {
    if (myInterestedInReferencesOnly) {
      return;
    }
    Object value = literal.getValue();
    if (value != null) {
      String stringValue = String.valueOf(value);
      int i = literal.getText().indexOf(stringValue);
      if (i >= 0) {
        TextRange range = TextRange.create(literal.getTextOffset() + i, literal.getTextOffset() + i + stringValue.length());
        myContext.addCachedValue(stringValue, range);
      }
    }
  }

  @Override
  public void visitGStringExpression(GrString expression) {
    GroovyPsiElement[] parts = expression.getAllContentParts();
    boolean registeredAssignment = false;
    for (GroovyPsiElement part : parts) {
      if (part instanceof GrStringContent) {
        if (!myInterestedInReferencesOnly && !registeredAssignment && !part.getTextRange().isEmpty()) {
          registeredAssignment = true;
          String text = expression.getText();
          TextRange range = expression.getTextRange();
          if (text.startsWith("'") || text.startsWith("\"")) {
            text = text.substring(1);
            range = TextRange.create(range.getStartOffset() + 1, range.getEndOffset());
          }
          if (text.endsWith("'") || text.endsWith("\"")) {
            text = text.substring(0, text.length() - 1);
            range = TextRange.create(range.getStartOffset(), range.getEndOffset() - 1);
          }
          myContext.addCachedValue(text, range);
        }
      }
      else if (part instanceof GrStringInjection) {
        GrExpression injectedExpression = ((GrStringInjection)part).getExpression();
        if (injectedExpression != null) {
          injectedExpression.accept(myVisitor);
        }
      }
    }
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression expression) {
    if (expression.getParent() instanceof GrMethodCallExpression) {
      // This is a reference expression which points to the method name. We're not interested in it, so, just return.
      return;
    }
    GrExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) {
      myContext.addCachedVariable(expression.getText(), expression.getTextRange());
      return;
    }
    myContext.rememberVariableQualifier(qualifier.getText());
    PsiElement dotToken = expression.getDotToken();
    if (dotToken == null) {
      return;
    }
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.findSingle(GroovyLanguage.INSTANCE);
    for (PsiElement e = dotToken.getNextSibling(); e != null; e = e.getNextSibling()) {
      ASTNode node = e.getNode();
      if (node == null) {
        if (e instanceof PsiWhiteSpace) {
          continue;
        }
        e.accept(myVisitor);
        return;
      }
      IElementType type = node.getElementType();
      if (type == GroovyTokenTypes.mIDENT) {
        myContext.addCachedVariable(e.getText(), e.getTextRange());
      }
      else if (parserDefinition.getWhitespaceTokens().contains(type) || parserDefinition.getCommentTokens().contains(type)) {
        continue;
      }
      else {
        e.accept(myVisitor);
      }
      return;
    }
  }

  @Override
  public void visitElement(GroovyPsiElement element) {
    if (!myInterestedInReferencesOnly) {
      // Consider every expression over than those we explicitly prepared for (e.g. literal expression, reference expression etc)
      // to be registered as a definition source binding within the context. However, we want to correctly handle a situation like
      // a method call, e.g. 'System.getenv("VAR")' - here the whole method call expression is given to this method first and
      // we register a variable at the context for it. However, later on expression '("VAR")' is given to this method - we don't
      // want to register is because it's already registered for the outer scope.
      myInterestedInReferencesOnly = true;
      myContext.addCachedValue("", element.getTextRange());
    }
    super.visitElement(element);
  }
}

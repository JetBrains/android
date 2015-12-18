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
package com.android.tools.idea.gradle.dsl.parser;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static com.intellij.openapi.util.text.StringUtil.isQuotedString;
import static com.intellij.openapi.util.text.StringUtil.unquoteString;
import static com.intellij.psi.util.PsiTreeUtil.findChildOfType;

public final class PsiElements {
  private PsiElements() {
  }

  @Nullable
  public static String getUnquotedText(@NotNull GrLiteral literal) {
    String text = literal.getText();
    if (text != null && text.length() > 2 && isQuotedString(text)) {
      return unquoteString(text);
    }
    return null;
  }

  /**
   * Returns the closure block inside the given expression, if the expression's text matches any the given names.
   * <p>
   * An example of an expression with a closure block:
   * <pre>
   *   dependencies {
   *
   *   }
   * </pre>
   * To get the closure block, the name of to pass to this method is "dependencies".
   * </p>
   *
   * @param expression         the given expression.
   * @param possibleBlockNames the names to match.
   * @return the found closure block, or {@code null} if the expression name does not match the given ones.
   */
  @Nullable
  public static GrClosableBlock findClosableBlock(@NotNull GrMethodCallExpression expression, @NotNull String...possibleBlockNames) {
    GrExpression invokedExpression = expression.getInvokedExpression();
    GrReferenceExpression childExpression = findChildOfType(invokedExpression, GrReferenceExpression.class, false);
    if (isNotNullWithText(childExpression, possibleBlockNames)) {
      GrClosableBlock[] closureArguments = expression.getClosureArguments();
      if (closureArguments.length > 0) {
        GrClosableBlock closableBlock = closureArguments[0];
        if (closableBlock != null) {
          return closableBlock;
        }
      }
    }
    return null;
  }

  public static boolean isNotNullWithText(@Nullable PsiElement e, @NotNull String...possibleTextValues) {
    if (e != null) {
      String elementText = e.getText();
      for (String text : possibleTextValues) {
        if (text.equals(elementText)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Sets the text of the given literal.
   * @param literal the given literal.
   * @param text the text to set.
   * @return the new literal created when the text was set.
   */
  public static GrLiteral setLiteralText(@NotNull GrLiteral literal, @NotNull String text) {
    Project project = literal.getProject();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    GrLiteral newLiteral = factory.createLiteralFromValue(text);
    PsiElement replace = literal.replace(newLiteral);
    assert replace instanceof GrLiteral;
    return (GrLiteral)replace;
  }
}

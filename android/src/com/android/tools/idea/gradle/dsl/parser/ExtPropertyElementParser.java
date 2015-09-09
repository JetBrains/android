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

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static com.android.tools.idea.gradle.dsl.parser.PsiElements.findClosableBlock;
import static com.android.tools.idea.gradle.dsl.parser.PsiElements.isNotNullWithText;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.psi.util.PsiTreeUtil.getChildrenOfType;

class ExtPropertyElementParser implements GradleDslElementParser {
  @NonNls private static final String EXT = "ext";

  @Override
  public boolean parse(@NotNull GroovyPsiElement e, @NotNull GradleBuildModel buildModel) {
    if (e instanceof GrAssignmentExpression) {
      return parseQualifiedExtProperty((GrAssignmentExpression)e, buildModel);
    }
    if (e instanceof GrMethodCallExpression) {
      GrMethodCallExpression expression = (GrMethodCallExpression)e;
      GrClosableBlock closableBlock = findClosableBlock(expression, EXT);
      if (closableBlock != null) {
        parseExtProperty(closableBlock, buildModel);
        return true;
      }
    }
    return false;
  }

  private static boolean parseQualifiedExtProperty(@NotNull GrAssignmentExpression expression, @NotNull GradleBuildModel buildModel) {
    GrExpression left = expression.getLValue();
    if (!(left instanceof GrReferenceExpression)) {
      return false;
    }
    GrReferenceExpression reference = (GrReferenceExpression)left;
    GrExpression qualifierExpression = reference.getQualifierExpression();
    if (isNotNullWithText(qualifierExpression, EXT)) {
      return parseExtProperty(expression, buildModel);
    }
    return false;
  }

  private static void parseExtProperty(@NotNull GrClosableBlock closableBlock, @NotNull GradleBuildModel buildModel) {
    GrAssignmentExpression[] expressions = getChildrenOfType(closableBlock, GrAssignmentExpression.class);
    if (expressions != null) {
      for (GrAssignmentExpression expression : expressions) {
        parseExtProperty(expression, buildModel);
      }
    }
  }

  private static boolean parseExtProperty(@NotNull GrAssignmentExpression expression, @NotNull GradleBuildModel buildModel) {
    GrExpression left = expression.getLValue();
    if (!(left instanceof GrReferenceExpression)) {
      return false;
    }
    PsiElement operationToken = expression.getOperationToken();
    if (operationToken.getText().equals("=")) {
      // TODO Also support ext.set("xxx", y")
      GrExpression right = expression.getRValue();
      if (right == null) {
        return false;
      }
      GrReferenceExpression reference = (GrReferenceExpression)left;
      String name = reference.getReferenceName();
      if (isEmpty(name)) {
        return false;
      }
      ExtPropertyElement extProperty = new ExtPropertyElement(name, right);
      buildModel.addExtProperty(extProperty);
      return true;
    }
    return false;
  }
}

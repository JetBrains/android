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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

class ExtPropertyElementParser implements GradleDslElementParser {
  @Override
  public boolean parse(@NotNull GroovyPsiElement e, @NotNull GradleBuildModel buildFile) {
    if (e instanceof GrAssignmentExpression) {
      GrAssignmentExpression expression = (GrAssignmentExpression)e;
      GrExpression left = expression.getLValue();
      if (left instanceof GrReferenceExpression) {
        GrReferenceExpression reference = (GrReferenceExpression)left;
        GrExpression qualifierExpression = reference.getQualifierExpression();
        if (qualifierExpression != null && "ext".equals(qualifierExpression.getText())) {
          // We have found that the left side of the assignment starts with "ext."
          PsiElement operationToken = expression.getOperationToken();
          if (operationToken.getText().equals("=")) {
            GrExpression right = expression.getRValue();
            if (right != null) {
              String name = reference.getReferenceName();
              if (StringUtil.isNotEmpty(name)) {
                ExtPropertyElement extProperty = new ExtPropertyElement(name, right);
                buildFile.addExtProperty(extProperty);
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }
}

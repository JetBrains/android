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
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.ListLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.psi.PsiElement;
import javax.annotation.Nullable;

/** Checks that glob expressions are valid */
public class GlobErrorAnnotator extends BuildAnnotator {

  @Override
  public void visitGlobExpression(GlobExpression node) {
    Argument[] args = node.getArguments();
    boolean hasIncludes = false;
    for (int i = 0; i < args.length; i++) {
      Argument arg = args[i];
      String name = arg instanceof Argument.Keyword ? arg.getName() : null;
      if ("include".equals(name) || (arg instanceof Argument.Positional && i == 0)) {
        hasIncludes = checkIncludes(arg.getValue());
      } else if ("exclude".equals(name)) {
        checkListContents("exclude", arg.getValue());
      } else if ("exclude_directories".equals(name)) {
        checkExcludeDirsNode(arg);
      } else {
        markError(arg, "Unrecognized glob argument");
      }
    }
    if (!hasIncludes) {
      markError(node, "Glob expression must contain at least one included string");
    }
  }

  private void checkExcludeDirsNode(Argument arg) {
    Expression value = arg.getValue();
    if (value == null || !(value.getText().equals("0") || value.getText().equals("1"))) {
      markError(arg, "exclude_directories parameter to glob must be 0 or 1");
    }
  }

  /** @return true if glob contains at least one included string */
  private boolean checkIncludes(@Nullable Expression expr) {
    return checkListContents("include", expr);
  }

  /**
   * @return false if 'expr' is known with certainty not to be a list containing at least one string
   */
  private boolean checkListContents(String keyword, @Nullable Expression expr) {
    if (expr == null) {
      return false;
    }
    PsiElement rootElement = PsiUtils.getReferencedTargetValue(expr);
    if (rootElement instanceof ListLiteral) {
      return validatePatternList(keyword, ((ListLiteral) rootElement).getChildExpressions());
    }
    if (rootElement instanceof ReferenceExpression
        || !BuildElementValidation.possiblyValidListLiteral(rootElement)) {
      markError(expr, "Glob parameter '" + keyword + "' must be a list of strings");
      return false;
    }
    // might possibly be a list, default to not showing any errors
    return true;
  }

  /** @return false if 'expr' is known with certainty not to contain at least one string */
  private boolean validatePatternList(String keyword, Expression[] expressions) {
    boolean possiblyHasString = false;
    for (Expression expr : expressions) {
      PsiElement rootElement = PsiUtils.getReferencedTargetValue(expr);
      if (rootElement instanceof ReferenceExpression
          || !BuildElementValidation.possiblyValidStringLiteral(rootElement)) {
        markError(expr, "Glob parameter '" + keyword + "' must be a list of strings");
      } else {
        possiblyHasString = true;
        if (rootElement instanceof StringLiteral) {
          validatePattern((StringLiteral) rootElement);
        }
      }
    }
    return possiblyHasString;
  }

  private void validatePattern(StringLiteral pattern) {
    String error = GlobPatternValidator.validate(pattern.getStringContents());
    if (error != null) {
      markError(pattern, error);
    }
  }
}

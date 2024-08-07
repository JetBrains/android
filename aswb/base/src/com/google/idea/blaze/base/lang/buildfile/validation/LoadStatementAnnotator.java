/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadedSymbol;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.quickfix.DeprecatedLoadQuickFix;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.HighlightSeverity;
import javax.annotation.Nullable;

/** Adds warning/error annotations to load statements. */
public class LoadStatementAnnotator extends BuildAnnotator {

  @Override
  public void visitLoadStatement(LoadStatement node) {
    validateImportTarget(node.getImportPsiElement());
  }

  @Override
  public void visitLoadedSymbol(LoadedSymbol node) {
    StringLiteral loadedString = node.getImport();
    if (loadedString == null) {
      return;
    }
    String name = loadedString.getStringContents();
    if (name.startsWith("_")) {
      markError(node, String.format("Symbol '%s' is private and cannot be imported.", name));
    }
  }

  private void validateImportTarget(@Nullable StringLiteral target) {
    if (target == null) {
      return;
    }
    String targetString = target.getStringContents();
    if (targetString == null
        || targetString.startsWith(":")
        || targetString.startsWith("//")
        || targetString.startsWith("@")
        || targetString.length() < 2) {
      return;
    }
    if (targetString.startsWith("/")) {
      String message = "Deprecated load syntax; loaded Starlark module should be in label format.";
      InspectionManager inspectionManager = InspectionManager.getInstance(target.getProject());
      ProblemDescriptor descriptor =
          inspectionManager.createProblemDescriptor(
              target,
              message,
              DeprecatedLoadQuickFix.INSTANCE,
              ProblemHighlightType.LIKE_DEPRECATED,
              true);
      getHolder()
          .newAnnotation(HighlightSeverity.WARNING, message)
          .range(target)
          .newLocalQuickFix(DeprecatedLoadQuickFix.INSTANCE, descriptor)
          .registerFix()
          .create();
      return;
    }
    markError(target, "Invalid load syntax: missing Starlark module.");
  }
}

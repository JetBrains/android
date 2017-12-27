/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.tools.lint.checks.CallSuperDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintMissingSuperCallInspection extends AndroidLintInspectionBase {
  public AndroidLintMissingSuperCallInspection() {
    super(AndroidBundle.message("android.lint.inspections.missing.super.call"), CallSuperDetector.ISSUE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    return new AndroidLintQuickFix[]{
      new DefaultLintQuickFix("Add super call") {
        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          PsiMethod method = PsiTreeUtil.getParentOfType(startElement, PsiMethod.class);
          assert method != null;
          Project project = startElement.getProject();

          PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
          // Create the statement to be added as the first one in the method.
          // e.g. super.onCreate(savedInstanceState);
          PsiStatement superStatement = factory.createStatementFromText(buildSuperStatement(method), null);

          PsiCodeBlock body = method.getBody();
          if (body != null) {
            PsiStatement[] statements = body.getStatements();
            if (statements.length > 0) {
              body.addBefore(superStatement, statements[0]);
            }
            else {
              // Remove whitespace in the body that does not have statements
              // Only removed if the body has no comments.
              PsiWhiteSpace whiteSpace = PsiTreeUtil.getChildOfType(method.getBody(), PsiWhiteSpace.class);
              if (whiteSpace != null && whiteSpace.getText().startsWith("\n\n")) {
                method.getBody().replace(factory.createCodeBlock());
                body = method.getBody();
              }
              body.add(superStatement);
            }
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(body);
            CodeStyleManager.getInstance(project).reformat(body);
          }
        }

        @NotNull
        private String buildSuperStatement(PsiMethod method) {
          StringBuilder methodCallText = new StringBuilder("super.")
            .append(method.getName()).append('(');
          PsiParameter[] parameters = method.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            methodCallText.append(parameters[i].getName());
            if (i + 1 != parameters.length) {
              methodCallText.append(",");
            }
          }
          methodCallText.append(");");
          return methodCallText.toString();
        }

        @Override
        public boolean isApplicable(@NotNull PsiElement startElement,
                                    @NotNull PsiElement endElement,
                                    @NotNull AndroidQuickfixContexts.ContextType contextType) {
          PsiMethod type = PsiTreeUtil.getParentOfType(startElement, PsiMethod.class);
          return startElement.getLanguage() == JavaLanguage.INSTANCE
                 && type != null
                 // @CallSuper has an @Target(METHOD) currently. If it changes in the future
                 // revisit the constructor handling.
                 && !type.isConstructor();
        }
      }
    };
  }
}

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

import com.android.tools.lint.checks.AnnotationDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.TextFormat;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AndroidLintSwitchIntDefInspection extends AndroidLintInspectionBase {
  public AndroidLintSwitchIntDefInspection() {
    super(AndroidBundle.message("android.lint.inspections.switch.int.def"), AnnotationDetector.SWITCH_TYPE_DEF);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    @SuppressWarnings("unchecked")
    List<String> missingCases = LintFix.getData(fixData, List.class);
    if (missingCases != null && !missingCases.isEmpty()) {
      return new AndroidLintQuickFix[]{new AndroidLintQuickFix() {
        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          if (startElement.getParent() instanceof PsiSwitchStatement) {
            PsiSwitchStatement switchStatement = (PsiSwitchStatement)startElement.getParent();
            Project project = switchStatement.getProject();
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

            PsiCodeBlock body = switchStatement.getBody();
            if (body == null) {
              return;
            }
            PsiElement anchor = body.getLastChild();
            for (String constant : missingCases) {
              // The list we get from lint is using raw formatting, surrounding constants like `this`
              constant = TextFormat.RAW.convertTo(constant, TextFormat.TEXT);
              PsiElement parent = anchor.getParent();
              PsiStatement caseStatement = factory.createStatementFromText("case " + constant + ":", anchor);
              parent.addBefore(caseStatement, anchor);
              PsiStatement breakStatement = factory.createStatementFromText("break;", anchor);
              parent.addBefore(breakStatement, anchor);
            }

            CodeStyleManager.getInstance(project).reformat(switchStatement);
          }
        }

        @Override
        public boolean isApplicable(@NotNull PsiElement startElement,
                                    @NotNull PsiElement endElement,
                                    @NotNull AndroidQuickfixContexts.ContextType contextType) {
          return startElement.isValid();
        }

        @NotNull
        @Override
        public String getName() {
          return "Add Missing @IntDef Constants";
        }
      }};
    }

    return super.getQuickFixes(startElement, endElement, message, fixData);
  }
}

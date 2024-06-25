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
package com.android.tools.idea.lint.common;

import static com.intellij.codeInsight.intention.preview.IntentionPreviewUtils.prepareElementForWrite;

import com.android.tools.lint.checks.CallSuperDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtNamedFunction;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtPsiFactory;

public class AndroidLintMissingSuperCallInspection extends AndroidLintInspectionBase {
  public AndroidLintMissingSuperCallInspection() {
    super(LintBundle.message("android.lint.inspections.missing.super.call"), CallSuperDetector.ISSUE);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    return new LintIdeQuickFix[]{
      new DefaultLintQuickFix("Add super call", true) {
        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          if (!prepareElementForWrite(startElement)) {
            return;
          }
          PsiMethod superMethod = LintFix.getMethod(fixData, CallSuperDetector.KEY_METHOD);
          if (startElement.getLanguage() == JavaLanguage.INSTANCE) {
            PsiMethod method = PsiTreeUtil.getParentOfType(startElement, PsiMethod.class);
            if (method == null || method.isConstructor()) {
              return;
            }
            Project project = startElement.getProject();

            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            // Create the statement to be added as the first one in the method.
            // e.g. super.onCreate(savedInstanceState);
            PsiStatement superStatement = factory.createStatementFromText(buildSuperStatement(method, superMethod), null);

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
              PsiElement shortened = JavaCodeStyleManager.getInstance(project).shortenClassReferences(body);
              CodeStyleManager.getInstance(project).reformat(shortened);
            }
          }
          else if (startElement.getLanguage() == KotlinLanguage.INSTANCE) {
            Project project = startElement.getProject();
            KtPsiFactory factory = new KtPsiFactory(project);
            KtNamedFunction method = PsiTreeUtil.getParentOfType(startElement, KtNamedFunction.class);
            if (method == null) {
              return;
            }

            String superCall = buildSuperStatement(method, superMethod);
            KtExpression superStatement = factory.createExpression(superCall);
            KtBlockExpression bodyBlock = method.getBodyBlockExpression();

            if (bodyBlock == null) {
              KtExpression body = method.getBodyExpression();
              if (body != null) {
                PsiElement eq = null;
                PsiElement prev = body.getPrevSibling();
                while (prev != null) {
                  if (prev instanceof TreeElement && ((TreeElement)prev).getElementType() == KtTokens.EQ) {
                    eq = prev;
                    break;
                  }
                  prev = prev.getPrevSibling();
                }
                PsiElement parent = body.getParent();
                bodyBlock = factory.createSingleStatementBlock(body, null, null);
                body.delete();
                if (eq != null) {
                  eq.delete();
                }

                bodyBlock = (KtBlockExpression)parent.add(bodyBlock);
              }
            }
            if (bodyBlock != null) {
              PsiElement lBrace = bodyBlock.getLBrace();
              if (lBrace != null) {
                bodyBlock.addAfter(superStatement, lBrace);
              }
              else {
                List<KtExpression> statements = bodyBlock.getStatements();
                if (!statements.isEmpty()) {
                  bodyBlock.addBefore(superStatement, statements.get(0));
                }
              }
            }
          }
        }

        @NotNull
        private String buildSuperStatement(PsiMethod method, PsiMethod superMethod) {
          StringBuilder methodCallText = new StringBuilder();
          if (superMethod == null) {
            PsiMethod[] superMethods = method.findSuperMethods();
            if (superMethods.length > 0) {
              superMethod = superMethods[0];
            }
          }
          if (superMethod != null) {
            PsiClass containingClass = superMethod.getContainingClass();
            if (containingClass != null && containingClass.isInterface()) {
              methodCallText.append(containingClass.getQualifiedName()).append('.');
            }
          }

          methodCallText.append("super.").append(method.getName()).append('(');
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

        @NotNull
        private String buildSuperStatement(KtNamedFunction method, PsiMethod superMethod) {
          StringBuilder methodCallText = new StringBuilder();
          methodCallText.append("super.").append(method.getName()).append('(');
          List<KtParameter> parameters = method.getValueParameters();
          for (int i = 0; i < parameters.size(); i++) {
            methodCallText.append(parameters.get(i).getName());
            if (i + 1 != parameters.size()) {
              methodCallText.append(",");
            }
          }
          methodCallText.append(")");
          return methodCallText.toString();
        }

        @Override
        public boolean isApplicable(@NotNull PsiElement startElement,
                                    @NotNull PsiElement endElement,
                                    @NotNull AndroidQuickfixContexts.ContextType contextType) {
          return true;
        }
      }
    };
  }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.inspections;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.lint.LintIdeClient;
import com.android.tools.idea.lint.LintIdeUtils;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.lint.checks.ApiLookup;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidDeprecationFilter extends AndroidDeprecationInspection.DeprecationFilter {
  private static final String ACTION_BAR_ACTIVITY = "android.support.v7.app.ActionBarActivity";
  private static final String APP_COMPAT_ACTIVITY = "android.support.v7.app.AppCompatActivity";

  private static int getDeprecatedIn(@Nullable Project project, @NotNull PsiElement deprecatedElement) {
    if (project == null) {
      return -1;
    }
    ApiLookup apiLookup = LintIdeClient.getApiLookup(project);
    if (apiLookup == null) {
      return -1;
    }

    if (deprecatedElement instanceof PsiClass) {
      String owner = LintIdeUtils.getInternalName((PsiClass)deprecatedElement);
      if (owner != null) {
        return apiLookup.getClassDeprecatedIn(owner);
      }
    }
    else if (deprecatedElement instanceof PsiMember) {
      PsiClass containingClass = ((PsiMember)deprecatedElement).getContainingClass();
      if (containingClass != null) {
        String owner = LintIdeUtils.getInternalName(containingClass);
        if (owner != null) {
          if (deprecatedElement instanceof PsiField) {
            String name = ((PsiField)deprecatedElement).getName();
            if (name != null) {
              return apiLookup.getFieldDeprecatedIn(owner, name);
            }
          }
          else if (deprecatedElement instanceof PsiMethod) {
            PsiMethod method = (PsiMethod)deprecatedElement;
            String name = LintIdeUtils.getInternalMethodName(method);
            String desc = LintIdeUtils.getInternalDescription(method, false, false);
            if (desc != null) {
              return apiLookup.getCallDeprecatedIn(owner, name, desc);
            }
          }
        }
      }
    }

    return -1;
  }

  @Override
  public boolean isExcluded(@NotNull PsiElement deprecatedElement, @NotNull PsiElement referenceElement, @Nullable String symbolName) {
    Project project = referenceElement.getProject();
    int deprecatedIn = getDeprecatedIn(project, deprecatedElement);
    if (deprecatedIn != -1) {
      AndroidFacet facet = AndroidFacet.getInstance(referenceElement);
      if (facet != null && !facet.isDisposed() && AndroidModuleInfo.getInstance(facet).getMinSdkVersion().getApiLevel() < deprecatedIn) {
        return !(VersionChecks.isPrecededByVersionCheckExit(referenceElement, deprecatedIn) ||
                 VersionChecks.isWithinVersionCheckConditional(referenceElement, deprecatedIn));
      }
    }

    return false;
  }

  /**
   * @param deprecatedElement the deprecated element (e.g. the deprecated class, method or field)
   * @param referenceElement  the reference to that deprecated element
   * @param symbolName        the user visible symbol name
   * @param defaultMessage    the default message to be shown for this deprecation
   * @return
   */
  @NotNull
  @Override
  public String getDeprecationMessage(@NotNull PsiElement deprecatedElement,
                                      @NotNull PsiElement referenceElement,
                                      @Nullable String symbolName,
                                      @NotNull String defaultMessage) {
    if (ACTION_BAR_ACTIVITY.equals(symbolName)) {
      return "ActionBarActivity is deprecated; use `AppCompatActivity` instead";
    }

    int version = getDeprecatedIn(referenceElement.getProject(), deprecatedElement);
    if (version != -1) {
      return defaultMessage + " as of " + SdkVersionInfo.getAndroidName(version);
    }

    return defaultMessage;
  }

  @NotNull
  @Override
  public LocalQuickFix[] getQuickFixes(@NotNull PsiElement deprecatedElement,
                                       @NotNull PsiElement referenceElement,
                                       @Nullable String symbolName) {
    if (ACTION_BAR_ACTIVITY.equals(symbolName)) {
      return new LocalQuickFix[] {new ReplaceSuperClassFix(referenceElement, APP_COMPAT_ACTIVITY)};
    }

    return LocalQuickFix.EMPTY_ARRAY;
  }

  private static class ReplaceSuperClassFix implements LocalQuickFix {
    private final String myQualifiedName;
    @NotNull
    private final SmartPsiElementPointer<PsiElement> myElement;

    public ReplaceSuperClassFix(@NotNull PsiElement element, @NotNull String qualifiedName) {
      myQualifiedName = qualifiedName;
      myElement = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace With " + myQualifiedName.substring(myQualifiedName.lastIndexOf('.') + 1);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace deprecated code";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement replace = myElement.getElement();
      if (replace == null) {
        return;
      }
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiElementFactory elementFactory = facade.getElementFactory();
      PsiElement newReference = elementFactory.createReferenceFromText(myQualifiedName, replace);
      newReference = replace.replace(newReference);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(newReference);
    }
  }
}
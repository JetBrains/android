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

import com.android.sdklib.AndroidVersionUtils;
import com.android.support.AndroidxNameUtils;
import com.android.tools.idea.lint.common.LintIdeClient;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.lint.checks.ApiLookup;
import com.android.tools.lint.detector.api.ApiConstraint;
import com.android.tools.lint.detector.api.ExtensionSdk;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.helpers.DefaultJavaEvaluator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidDeprecationFilter extends AndroidDeprecationInspection.DeprecationFilter {
  private static final String ACTION_BAR_ACTIVITY = "android.support.v7.app.ActionBarActivity";
  private static final String APP_COMPAT_ACTIVITY = "android.support.v7.app.AppCompatActivity";

  @NotNull
  private static ApiConstraint getDeprecatedIn(@Nullable Project project, @NotNull PsiElement deprecatedElement) {
    if (project == null) {
      return ApiConstraint.UNKNOWN;
    }
    ApiLookup apiLookup = LintIdeClient.getApiLookup(project);
    if (apiLookup == null) {
      return ApiConstraint.UNKNOWN;
    }

    if (deprecatedElement instanceof PsiClass) {
      String owner = ((PsiClass)deprecatedElement).getQualifiedName();
      if (owner != null) {
        return apiLookup.getClassDeprecatedInVersions(owner);
      }
    }
    else if (deprecatedElement instanceof PsiMember) {
      PsiClass containingClass = ((PsiMember)deprecatedElement).getContainingClass();
      if (containingClass != null) {
        DefaultJavaEvaluator evaluator = new DefaultJavaEvaluator(project, null);
        String owner = containingClass.getQualifiedName();
        if (owner != null) {
          if (deprecatedElement instanceof PsiField) {
            String name = ((PsiField)deprecatedElement).getName();
            return apiLookup.getFieldDeprecatedInVersions(owner, name);
          }
          else if (deprecatedElement instanceof PsiMethod) {
            PsiMethod method = (PsiMethod)deprecatedElement;
            String name = Lint.getInternalMethodName(method);
            String desc = evaluator.getMethodDescription(method, false, false);
            if (desc != null) {
              return apiLookup.getMethodDeprecatedInVersions(owner, name, desc);
            }
          }
        }
      }
    }

    return ApiConstraint.UNKNOWN;
  }

  @Override
  public boolean isExcluded(@NotNull PsiElement deprecatedElement, @NotNull PsiElement referenceElement, @Nullable String symbolName) {
    Project project = referenceElement.getProject();
    ApiConstraint deprecatedInVersions = getDeprecatedIn(project, deprecatedElement);
    if (deprecatedInVersions != ApiConstraint.UNKNOWN) {
      int deprecatedIn = deprecatedInVersions.min();
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
   * @return the deprecation message to display
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

    ApiConstraint version = getDeprecatedIn(referenceElement.getProject(), deprecatedElement);
    if (version != ApiConstraint.UNKNOWN) {
      ApiConstraint.SdkApiConstraint sdk = version.findSdk(ExtensionSdk.ANDROID_SDK_ID, false);
      if (sdk != null) {
        return defaultMessage + " as of " + AndroidVersionUtils.computeFullApiName(
            sdk.min(),
            /*extensionLevel*/ null,
            /*includeReleaseName*/ true,
            /*includeCodeName*/ true);
      } else {
        return defaultMessage + " as of " + version.min();
      }
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
      boolean androidX = MigrateToAndroidxUtil.isAndroidx(project);
      String newName = androidX ? AndroidxNameUtils.getNewName(myQualifiedName) : myQualifiedName;
      PsiElement newReference = elementFactory.createReferenceFromText(newName, replace);
      newReference = replace.replace(newReference);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(newReference);
    }
  }
}

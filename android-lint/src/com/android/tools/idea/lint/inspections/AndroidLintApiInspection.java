/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.inspections;

import static com.android.tools.lint.detector.api.ExtensionSdk.ANDROID_SDK_ID;
import static com.android.tools.lint.detector.api.VersionChecks.REQUIRES_API_ANNOTATION;
import static com.android.tools.lint.detector.api.VersionChecks.REQUIRES_EXTENSION_ANNOTATION;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.quickFixes.AddTargetApiQuickFix;
import com.android.tools.idea.lint.quickFixes.AddTargetVersionCheckQuickFix;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.detector.api.ApiConstraint;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.intentions.OverrideResourceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AndroidLintApiInspection extends AndroidLintInspectionBase {
  public AndroidLintApiInspection(String displayName, Issue issue) {
    super(displayName, issue);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    ApiConstraint requirement = LintFix.getApiConstraint(fixData, ApiDetector.KEY_REQUIRES_API, ApiConstraint.NONE);
    int api = requirement.min();
    if (api != -1) {
      List<LintIdeQuickFix> list = new ArrayList<>();
      PsiFile file = startElement.getContainingFile();
      boolean isXml = false;
      if (file instanceof XmlFile) {
        isXml = true;
        ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
        if (folderType != null) {
          FolderConfiguration config = IdeResourcesUtil.getFolderConfiguration(file);
          if (config != null) {
            config.setVersionQualifier(new VersionQualifier(api));
            String folder = config.getFolderName(folderType);
            list.add(OverrideResourceAction.createFix(folder));
          }
        }
      }

      // Is the API fix limited to applying to (for example) just classes?
      boolean requireClass = LintFix.getBoolean(fixData, ApiDetector.KEY_REQUIRE_CLASS, false);
      List<ApiConstraint.SdkApiConstraint> constraints = requirement.getConstraints();
      if (!requireClass && !isXml) {
        ApiConstraint minSdk = LintFix.getApiConstraint(fixData, ApiDetector.KEY_MIN_API, ApiConstraint.NONE);
        for (ApiConstraint constraint : constraints) {
          int version = constraint.min();
          int sdk = constraint.getSdk();
          list.add(new AddTargetVersionCheckQuickFix(version, sdk, minSdk));
        }
      }

      ApplicationManager.getApplication().assertReadAccessAllowed();
      Project project = startElement.getProject();
      ApiConstraint.SdkApiConstraint first = constraints.get(0);
      if (constraints.size() > 1) {
        // Just pick the first one: the order is significant and the first item is supposed
        // to be the recommended one. We don't currently have an annotation mechanism to
        // declare "you must have *all* of these APIs".
        if (requiresSdkVersionAvailable(project)) {
          list.add(new AddTargetApiQuickFix(Collections.singletonList(first), true, startElement, requireClass));
        }
      } else {
        int sdkId = first.getSdkId();
        if (sdkId == ANDROID_SDK_ID) {
          if (!isXml && requiresApiAvailable(project)) {
            list.add(new AddTargetApiQuickFix(constraints, true, startElement, requireClass));
          }
          else {
            // Discourage use of @TargetApi if @RequiresApi is available; see for example
            // https://android-review.googlesource.com/c/platform/frameworks/support/+/843915/
            list.add(new AddTargetApiQuickFix(constraints, false, startElement, requireClass));
          }
        } else if (!isXml && requiresSdkVersionAvailable(project)) {
          list.add(new AddTargetApiQuickFix(constraints, true, startElement, requireClass));
        }
      }

      return list.toArray(LintIdeQuickFix.EMPTY_ARRAY);
    }

    return super.getQuickFixes(startElement, endElement, message, fixData);
  }

  public static boolean requiresApiAvailable(Project project) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return facade.findClass(REQUIRES_API_ANNOTATION.oldName(), scope) != null ||
           facade.findClass(REQUIRES_API_ANNOTATION.newName(), scope) != null;
  }

  public static boolean requiresSdkVersionAvailable(Project project) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return facade.findClass(REQUIRES_EXTENSION_ANNOTATION, scope) != null;
  }
}

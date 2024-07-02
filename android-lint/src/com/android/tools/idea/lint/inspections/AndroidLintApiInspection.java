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
import com.android.tools.idea.lint.common.ModCommandLintQuickFix;
import com.android.tools.idea.lint.quickFixes.AddTargetApiQuickFix;
import com.android.tools.idea.lint.quickFixes.AddTargetVersionCheckQuickFix;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.detector.api.ApiConstraint;
import com.android.tools.lint.detector.api.Incident;
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

  @Override
  public @NotNull LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                                  @NotNull PsiElement endElement,
                                                  @NotNull Incident incident) {
    LintFix fixData = incident.getFix();
    if (fixData != null) {
      ApiConstraint requirement = LintFix.getApiConstraint(fixData, ApiDetector.KEY_REQUIRES_API, ApiConstraint.UNKNOWN);
      if (requirement != ApiConstraint.UNKNOWN) {
        List<LintIdeQuickFix> list = getApiConstraintFixes(startElement, requirement, fixData);
        return list.toArray(LintIdeQuickFix.EMPTY_ARRAY);
      }
      else {
        LintIdeQuickFix[] fixes = getCombinedFixes(startElement, incident, fixData);
        if (fixes == null) {
          assert false : "Unexpected API fix data: " + fixData;
        } else {
          return fixes;
        }
      }
    }
    return super.getQuickFixes(startElement, endElement, incident);
  }

  private static LintIdeQuickFix @Nullable [] getCombinedFixes(@NotNull PsiElement startElement, @NotNull Incident incident, @NotNull LintFix fixData) {
    ApiConstraint requirement;
    if (fixData instanceof LintFix.LintFixGroup) {
      // We support exactly two types of quickfixes for API errors:
      // (1) Just an API constraint in a data map -- this implies that we should
      //     offer IDE-side quickfixes related to the API level, such as adding
      //     a RequiresApi annotation, suppressing, etc. (This was handled
      //     above.)
      // (2) A single regular quickfix descriptor, *and* a constraint data map, combined
      //     in that order in a fix alternatives group. We'll offer
      //     the fix followed by the API constraints fixes.
      LintFix.LintFixGroup group = (LintFix.LintFixGroup)fixData;
      if (group.getType() != LintFix.GroupType.ALTERNATIVES) {
        return null;
      }
      List<LintFix> fixes = group.getFixes();
      if (fixes.size() != 2) {
        return null;
      }

      LintIdeQuickFix[] quickFixes = createFixes(startElement.getProject(), startElement.getContainingFile(), incident, fixes.get(0));

      requirement = LintFix.getApiConstraint(fixes.get(1), ApiDetector.KEY_REQUIRES_API, ApiConstraint.UNKNOWN);
      if (requirement != ApiConstraint.UNKNOWN) {
        List<LintIdeQuickFix> list = getApiConstraintFixes(startElement, requirement, fixData);
        LintIdeQuickFix[] joined = new LintIdeQuickFix[quickFixes.length + list.size()];
        System.arraycopy(quickFixes, 0, joined, 0, quickFixes.length);
        for (int i = 0; i < list.size(); i++) {
          joined[i + quickFixes.length] = list.get(i);
        }
        return joined;
      }
      return quickFixes;
    } else {
      return createFixes(startElement.getProject(), startElement.getContainingFile(), incident, fixData);
    }
  }

  private static @NotNull List<LintIdeQuickFix> getApiConstraintFixes(@NotNull PsiElement startElement,
                                                                      @NotNull ApiConstraint requirement,
                                                                      @NotNull LintFix fixData) {
    int api = requirement.min();
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
    Project project = startElement.getProject();
    if (!requireClass && !isXml) {
      ApiConstraint minSdk = LintFix.getApiConstraint(fixData, ApiDetector.KEY_MIN_API, ApiConstraint.UNKNOWN);
      for (ApiConstraint constraint : constraints) {
        int version = constraint.min();
        int sdk = constraint.getSdk();
        list.add(new AddTargetVersionCheckQuickFix(project, version, sdk, minSdk));
      }
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    ApiConstraint.SdkApiConstraint first = constraints.get(0);
    if (constraints.size() > 1) {
      // Just pick the first one: the order is significant and the first item is supposed
      // to be the recommended one. We don't currently have an annotation mechanism to
      // declare "you must have *all* of these APIs".
      if (requiresExtensionAvailable(project)) {
        list.add(new ModCommandLintQuickFix(new AddTargetApiQuickFix(Collections.singletonList(first), true, startElement, requireClass)));
      }
    } else {
      int sdkId = first.getSdkId();
      if (sdkId == ANDROID_SDK_ID) {
        if (!isXml && requiresApiAvailable(project)) {
          list.add(new ModCommandLintQuickFix(new AddTargetApiQuickFix(constraints, true, startElement, requireClass)));
        }
        else {
          // Discourage use of @TargetApi if @RequiresApi is available; see for example
          // https://android-review.googlesource.com/c/platform/frameworks/support/+/843915/
          list.add(new ModCommandLintQuickFix(new AddTargetApiQuickFix(constraints, false, startElement, requireClass)));
        }
      } else if (!isXml && requiresExtensionAvailable(project)) {
        list.add(new ModCommandLintQuickFix(new AddTargetApiQuickFix(constraints, true, startElement, requireClass)));
      }
    }
    return list;
  }

  public static boolean requiresApiAvailable(Project project) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return facade.findClass(REQUIRES_API_ANNOTATION.oldName(), scope) != null ||
           facade.findClass(REQUIRES_API_ANNOTATION.newName(), scope) != null;
  }

  public static boolean requiresExtensionAvailable(Project project) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return facade.findClass(REQUIRES_EXTENSION_ANNOTATION, scope) != null;
  }
}

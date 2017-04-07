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

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.actions.OverrideResourceAction;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.lint.checks.ApiDetector.REQUIRES_API_ANNOTATION;
import static com.android.tools.lint.detector.api.TextFormat.RAW;

public class AndroidLintNewApiInspection extends AndroidLintInspectionBase {
  public AndroidLintNewApiInspection() {
    super(AndroidBundle.message("android.lint.inspections.new.api"), ApiDetector.UNSUPPORTED);
  }

  public static AndroidLintQuickFix[] getApiDetectorFixes(@NotNull Issue issue,
                                                          @NotNull PsiElement startElement,
                                                          @SuppressWarnings("UnusedParameters") @NotNull PsiElement endElement,
                                                          @NotNull String message) {
    // TODO: Return one for each parent context (declaration, method, class, outer class(es)
    int api = ApiDetector.getRequiredVersion(issue, message, RAW);
    if (api != -1) {
      List<AndroidLintQuickFix> list = Lists.newArrayList();
      PsiFile file = startElement.getContainingFile();
      boolean isXml = false;
      if (file instanceof XmlFile) {
        isXml = true;
        ResourceFolderType folderType = ResourceHelper.getFolderType(file);
        if (folderType != null) {
          FolderConfiguration config = ResourceHelper.getFolderConfiguration(file);
          if (config != null) {
            config.setVersionQualifier(new VersionQualifier(api));
            String folder = config.getFolderName(folderType);
            list.add(OverrideResourceAction.createFix(folder));
          }
        }
      }

      list.add(new AddTargetVersionCheckQuickFix(api));
      list.add(new AddTargetApiQuickFix(api, false, startElement));
      ApplicationManager.getApplication().assertReadAccessAllowed();
      Project project = startElement.getProject();
      if (!isXml && JavaPsiFacade.getInstance(project).findClass(REQUIRES_API_ANNOTATION,
                                                                 GlobalSearchScope.allScope(project)) != null) {
        list.add(new AddTargetApiQuickFix(api, true, startElement));
      }

      return list.toArray(new AndroidLintQuickFix[list.size()]);
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return getApiDetectorFixes(ApiDetector.UNSUPPORTED, startElement, endElement, message);
  }
}

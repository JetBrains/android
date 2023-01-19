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

import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.rendering.webp.ConvertToWebpAction;
import com.android.tools.lint.checks.IconDetector;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;

public class AndroidLintConvertToWebpInspection extends AndroidLintInspectionBase {
  public AndroidLintConvertToWebpInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.convert.to.webp"), IconDetector.WEBP_ELIGIBLE);
  }

  @Override
  @NotNull
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return new LintIdeQuickFix[]{
      new DefaultLintQuickFix("Convert images to WebP...") {
        @Override
        public boolean isApplicable(@NotNull PsiElement startElement,
                                    @NotNull PsiElement endElement,
                                    @NotNull AndroidQuickfixContexts.ContextType contextType) {
          return AndroidFacet.getInstance(startElement) != null;
        }

        @Override
        public boolean startInWriteAction() {
          // ConvertToWebpAction opens a modal dialog and thus cannot be called while holding the write lock.
          return false;
        }

        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          AndroidFacet facet = AndroidFacet.getInstance(startElement);
          if (facet != null) {
            AndroidModuleInfo info = StudioAndroidModuleInfo.getInstance(facet);
            int minSdkVersion = info.getMinSdkVersion().getApiLevel();
            List<VirtualFile> folders = ResourceFolderManager.getInstance(facet).getFolders();
            ConvertToWebpAction action = new ConvertToWebpAction();
            action.perform(startElement.getProject(), minSdkVersion, folders.toArray(VirtualFile.EMPTY_ARRAY));
          }
        }
      }
    };
  }
}

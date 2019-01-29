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

import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.webp.ConvertToWebpAction;
import com.android.tools.lint.checks.IconDetector;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AndroidLintConvertToWebpInspection extends AndroidLintInspectionBase {
  public AndroidLintConvertToWebpInspection() {
    super(AndroidBundle.message("android.lint.inspections.convert.to.webp"), IconDetector.WEBP_ELIGIBLE);
  }

  @Override
  @NotNull
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return new AndroidLintQuickFix[]{
      new DefaultLintQuickFix("Convert images to WebP...") {
        @Override
        public boolean isApplicable(@NotNull PsiElement startElement,
                                    @NotNull PsiElement endElement,
                                    @NotNull AndroidQuickfixContexts.ContextType contextType) {
          return AndroidFacet.getInstance(startElement) != null;
        }

        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          AndroidFacet facet = AndroidFacet.getInstance(startElement);
          if (facet != null) {
            AndroidModuleInfo info = AndroidModuleInfo.getInstance(facet);
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
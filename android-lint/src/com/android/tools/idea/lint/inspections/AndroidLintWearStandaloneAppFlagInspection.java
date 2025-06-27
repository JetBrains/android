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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.checks.WearStandaloneAppDetector.KEY_QFX_EXTRA_MISSING_META_DATA;
import static com.android.tools.lint.checks.WearStandaloneAppDetector.WEARABLE_STANDALONE_ATTR;
import static com.android.tools.lint.checks.WearStandaloneAppDetector.WEAR_STANDALONE_APP_ISSUE;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_METADATA;

import com.android.tools.idea.lint.AndroidLintBundle;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.common.DefaultLintQuickFix;
import com.android.tools.idea.lint.common.LintIdeQuickFix;
import com.android.tools.lint.detector.api.LintFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintWearStandaloneAppFlagInspection extends AndroidLintInspectionBase {
  public AndroidLintWearStandaloneAppFlagInspection() {
    super(AndroidLintBundle.message("android.lint.inspections.wear.standalone.app.flag"),
          WEAR_STANDALONE_APP_ISSUE);
  }

  @NotNull
  @Override
  public LintIdeQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                         @NotNull PsiElement endElement,
                                         @NotNull String message,
                                         @Nullable LintFix fixData) {
    boolean addMetadata = LintFix.getBoolean(fixData, KEY_QFX_EXTRA_MISSING_META_DATA, false);
    if (addMetadata) {
      return new LintIdeQuickFix[]{
        new DefaultLintQuickFix("Add meta-data element for '" + WEARABLE_STANDALONE_ATTR + "'", true) {
          @Override
          public void apply(@NotNull PsiElement startElement,
                            @NotNull PsiElement endElement,
                            @NotNull AndroidQuickfixContexts.Context context) {
            XmlTag parent = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
            if (parent == null || !NODE_APPLICATION.equals(parent.getName())) {
              return;
            }

            XmlTag nodeMetadata = parent.createChildTag(NODE_METADATA, null, null, false);
            // Find the right location for the meta-data tag under <application>.
            XmlTag[] currentMetadataTags = parent.findSubTags(NODE_METADATA);
            XmlTag addAfter = currentMetadataTags.length > 0 ? currentMetadataTags[currentMetadataTags.length - 1] : null;
            if (addAfter != null) {
              nodeMetadata = (XmlTag)parent.addAfter(nodeMetadata, addAfter);
            }
            else {
              nodeMetadata = parent.addSubTag(nodeMetadata, true);
            }

            if (nodeMetadata != null) {
              nodeMetadata.setAttribute(ATTR_NAME, ANDROID_URI, WEARABLE_STANDALONE_ATTR);
              nodeMetadata.setAttribute(ATTR_VALUE, ANDROID_URI, VALUE_TRUE);
            }
          }

          @Override
          public boolean isApplicable(@NotNull PsiElement startElement,
                                      @NotNull PsiElement endElement,
                                      @NotNull AndroidQuickfixContexts.ContextType contextType) {
            XmlTag parent = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
            return parent != null && TAG_APPLICATION.equals(parent.getName());
          }
        }
      };
    }

    return super.getQuickFixes(startElement, endElement, message, fixData);
  }
}

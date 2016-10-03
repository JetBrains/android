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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.checks.ManifestDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public class AndroidLintOldTargetApiInspection extends AndroidLintInspectionBase {
  public AndroidLintOldTargetApiInspection() {
    super(AndroidBundle.message("android.lint.inspections.old.target.api"), ManifestDetector.TARGET_NEWER);
  }

  private static int getHighestApi(PsiElement element) {
    int max = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
    AndroidFacet instance = AndroidFacet.getInstance(element);
    if (instance != null) {
      AndroidSdkData sdkData = instance.getSdkData();
      if (sdkData != null) {
        for (IAndroidTarget target : sdkData.getTargets()) {
          if (target.isPlatform()) {
            AndroidVersion version = target.getVersion();
            if (version.getApiLevel() > max && !version.isPreview()) {
              max = version.getApiLevel();
            }
          }
        }
      }
    }
    return max;
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    String highest = Integer.toString(getHighestApi(startElement)); // TODO: preview platform??
    String label = "Update targetSdkVersion to " + highest;
    if (startElement.getContainingFile() instanceof XmlFile) {
      return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(label, "targetSdkVersion\\s*=\\s*[\"'](.*)[\"']", highest)};
    }
    else if (startElement.getContainingFile() instanceof GroovyFile) {
      return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(label, null, highest)};
    }
    else {
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }
}

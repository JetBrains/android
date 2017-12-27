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
package com.android.tools.idea.lint;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.StudioSdkUtil;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.android.tools.lint.checks.FontDetector.MIN_APPSUPPORT_VERSION;

/**
 * Quickfix which updates the appcompat-v7 support library to the latest version
 * which is the minimum for downloadable font support
 * (and also installs it in the local maven repository if necessary)
 */
public class UpgradeAppCompatV7Fix implements AndroidLintQuickFix {
  @Override
  public void apply(@NotNull PsiElement startElement,
                    @NotNull PsiElement endElement,
                    @NotNull AndroidQuickfixContexts.Context context) {
    Module module = AndroidPsiUtils.getModuleSafely(startElement);
    apply(module);
  }

  public static void apply(@Nullable Module module) {
    if (module != null) {
      StudioSdkUtil.reloadRemoteSdkWithModalProgress();
      AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
      StudioLoggerProgressIndicator
        progress = new StudioLoggerProgressIndicator(AndroidLintFontValidationErrorInspection.class);

      RepoPackage p = SdkMavenRepository.findLatestVersion(MIN_APPSUPPORT_VERSION, sdkHandler, null, progress);
      if (p != null) {
        GradleCoordinate gc = SdkMavenRepository.getCoordinateFromSdkPath(p.getPath());
        if (gc != null) { // should always be the case unless the version suffix is somehow wrong
          // Update version dependency in the module. Note that this will trigger a sync too.
          GradleDependencyManager manager = GradleDependencyManager.getInstance(module.getProject());
          manager.updateLibrariesToVersion(module, Collections.singletonList(gc), null);
        }
      }
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return true;
  }

  @NotNull
  @Override
  public String getName() {
    return "Upgrade appcompat-v7 to recommended version";
  }
}

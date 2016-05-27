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
package com.android.tools.idea.uibuilder.actions;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.sdk.StudioSdkUtil;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionToolProvider;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT;

/**
 * Quickfix which updates the constraint layout version to the latest available
 * version (and also installs it in the local maven repository if necessary)
 */
public class UpgradeConstraintLayoutFix implements AndroidLintQuickFix {
  @Override
  public void apply(@NotNull PsiElement startElement,
                    @NotNull PsiElement endElement,
                    @NotNull AndroidQuickfixContexts.Context context) {
    GradleDependencyManager manager = GradleDependencyManager.getInstance(startElement.getProject());
    Module module = AndroidPsiUtils.getModuleSafely(startElement);
    if (module != null) {
      StudioSdkUtil.reloadRemoteSdkWithModalProgress();
      AndroidSdkHandler sdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
      StudioLoggerProgressIndicator
        progress = new StudioLoggerProgressIndicator(AndroidLintInspectionToolProvider.AndroidLintMissingConstraintsInspection.class);
      RepositoryPackages packages = sdkHandler.getSdkManager(progress).getPackages();
      GradleCoordinate gc = GradleCoordinate.parseCoordinateString(CONSTRAINT_LAYOUT_LIB_ARTIFACT + ":+");
      assert gc != null;
      Collection<? extends LocalPackage> localPackages = packages.getLocalPackages().values();
      RepoPackage constraintPackage = SdkMavenRepository.findBestPackageMatching(gc, localPackages);
      if (constraintPackage == null) {
        Collection<RemotePackage> remotePackages = packages.getRemotePackages().values();
        constraintPackage = SdkMavenRepository.findBestPackageMatching(gc, remotePackages);
        if (constraintPackage != null) {
          List<String> paths = Collections.singletonList(constraintPackage.getPath());
          ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(startElement.getProject(), paths);
          if (dialog != null && !dialog.showAndGet()) {
            // User cancelled - don't proceed to update dependency below
            return;
          }
        }
        else {
          // Not found. This should not happen.
          return;
        }
      }

      // NOT constraintPackage.getVersion(), which isn't the maven version but the revision of this package
      // (e.g. 1.0.0-alpha2 has version "1")
      // The version
      String version = constraintPackage.getPath();
      version = version.substring(version.lastIndexOf(RepoPackage.PATH_SEPARATOR) + 1);
      gc = GradleCoordinate.parseCoordinateString(CONSTRAINT_LAYOUT_LIB_ARTIFACT + ':' + version);
      if (gc != null) { // should always be the case unless the version suffix is somehow wrong
        // Update version dependency in the module. Note that this will trigger a sync too.
        manager.updateLibrariesToVersion(module, Collections.singletonList(gc), null);
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
    return "Upgrade to recommended version";
  }
}

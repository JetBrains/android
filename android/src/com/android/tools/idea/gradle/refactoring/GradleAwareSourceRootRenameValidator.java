/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.refactoring;

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidatorEx;
import com.intellij.util.ProcessingContext;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * Issues a warning if a user tries to rename source root of a gradle-backed module
 */
public class GradleAwareSourceRootRenameValidator implements RenameInputValidatorEx {

  private boolean myShowWarning;

  @Nullable
  @Override
  public String getErrorMessage(String newName, Project project) {
    return myShowWarning ? AndroidBundle.message("android.refactoring.gradle.warning.rename.source.root") : null;
  }

  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return PlatformPatterns.psiElement(PsiDirectory.class);
  }

  @Override
  public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
    // Unfortunately, RenameInputValidatorEx contract is unspecified at the main ide code at the moment but de-facto
    // its getErrorMessage() method is called only when this method returns true.

    myShowWarning = false;
    if (!(element instanceof PsiDirectory)) {
      assert false;
      return true;
    }

    VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
    Module[] modules = ModuleManager.getInstance(element.getProject()).getModules();
    for (Module module : modules) {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, module) ||
          isEmpty(ExternalSystemApiUtil.getExternalProjectPath(module))) {
        // Ignore modules not backed by gradle.
        continue;
      }
      VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false);
      for (VirtualFile sourceRoot : sourceRoots) {
        if (sourceRoot.equals(virtualFile)) {
          myShowWarning = true;
          return true;
        }
      }
    }
    return true;
  }
}

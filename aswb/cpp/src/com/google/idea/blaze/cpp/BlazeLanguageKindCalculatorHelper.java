/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCLanguageKindCalculatorHelper;
import javax.annotation.Nullable;

final class BlazeLanguageKindCalculatorHelper implements OCLanguageKindCalculatorHelper {

  private static boolean isEnabled(Project project) {
    return Blaze.getProjectType(project) == ProjectType.ASPECT_SYNC;
  }

  /** #api212: add @Override */
  @Nullable
  public OCLanguageKind getLanguageByPsiFile(PsiFile psiFile) {
    if (isEnabled(psiFile.getProject())) {
      return getLanguageFromExtension(psiFile.getFileType().getDefaultExtension());
    }
    return null;
  }

  @Nullable
  @Override
  public OCLanguageKind getSpecifiedLanguage(Project project, VirtualFile file) {
    if (isEnabled(project)) {
      return getLanguageFromExtension(file.getExtension());
    }
    return null;
  }

  @Nullable
  @Override
  public OCLanguageKind getLanguageByExtension(Project project, String name) {
    if (isEnabled(project)) {
      return getLanguageFromExtension(FileUtilRt.getExtension(name));
    }
    return null;
  }

  @Nullable
  private OCLanguageKind getLanguageFromExtension(String extension) {
    if (CFileExtensions.C_FILE_EXTENSIONS.contains(extension)) {
      return CLanguageKind.C;
    }
    if (CFileExtensions.CXX_FILE_EXTENSIONS.contains(extension)) {
      return CLanguageKind.CPP;
    }
    if (CFileExtensions.CXX_ONLY_HEADER_EXTENSIONS.contains(extension)) {
      return CLanguageKind.CPP;
    }
    return null;
  }
}

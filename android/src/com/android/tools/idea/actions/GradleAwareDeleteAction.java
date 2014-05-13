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
package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class GradleAwareDeleteAction extends DeleteAction {
  @Nullable
  @Override
  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
    boolean hasJarFile = false;
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null && Projects.isGradleProject(project)) {
      PsiElement[] selection = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      hasJarFile = hasJarFile(selection);
    }
    // We don't allow to remove jar files used as libraries (jars not in classpath can be deleted.)
    // By returning a null DeleteProvider the "Delete" action will not appear.
    return hasJarFile ? null : super.getDeleteProvider(dataContext);
  }

  private static boolean hasJarFile(@Nullable PsiElement[] selection) {
    if (selection != null) {
      for (PsiElement e : selection) {
        if (e instanceof PsiDirectory) {
          VirtualFile directory = ((PsiDirectory)e).getVirtualFile();
          if (SdkConstants.EXT_JAR.equals(directory.getExtension())) {
            return true;
          }
        }
      }
    }
    return false;
  }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;

import com.android.tools.idea.projectsystem.ProjectSystemBuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public final class ClassFileUtil {
  private ClassFileUtil() {}

  public static boolean isClassSourceFileNewerThanClassClassFile(
    @NotNull Module module,
    @NotNull String fqcn,
    @NotNull VirtualFile classFile
  ) {
    Project project = module.getProject();
    GlobalSearchScope scope = module.getModuleWithDependenciesScope();
    VirtualFile sourceFile =
      ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>)() -> {
        PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(fqcn, scope);
        if (psiClass == null) {
          return null;
        }
        PsiFile psiFile = psiClass.getContainingFile();
        if (psiFile == null) {
          return null;
        }
        return psiFile.getVirtualFile();
      });

    if (sourceFile == null) {
      return false;
    }

    // Edited but not yet saved?
    if (FileDocumentManager.getInstance().isFileModified(sourceFile)) {
      return true;
    }

    // Check timestamp
    long sourceFileModified = sourceFile.getTimeStamp();

    // User modifications on the source file might not always result on a new .class file.
    // We use the project modification time instead to display the warning more reliably.
    long lastBuildTimestamp = classFile.getTimeStamp();
    ProjectSystemBuildManager.BuildResult buildResult = getProjectSystem(project).getBuildManager().getLastBuildResult();
    Long projectBuildTimestamp =
      buildResult.getStatus() != ProjectSystemBuildManager.BuildStatus.UNKNOWN ? buildResult.getTimestampMillis() : null;
    if (projectBuildTimestamp != null) {
      lastBuildTimestamp = projectBuildTimestamp;
    }
    return sourceFileModified > lastBuildTimestamp && lastBuildTimestamp > 0L;
  }
}

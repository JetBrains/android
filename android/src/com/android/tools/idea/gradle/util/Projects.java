/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for {@link Project}s.
 */
public final class Projects {
  private Projects() {
  }

  /**
   * Compiles the given project and refreshes the directory at the given path after compilation is finished. This method refreshes the
   * directory asynchronously and recursively.
   *
   * @param project          the given project.
   * @param dirToRefreshPath the path of the directory to refresh after compilation is finished.
   */
  public static void compile(@NotNull Project project, @NotNull final String dirToRefreshPath) {
    CompilerManager.getInstance(project).make(new CompileStatusNotification() {
      @Override
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(dirToRefreshPath);
        if (rootDir != null && rootDir.isDirectory()) {
          rootDir.refresh(true, true);
        }
      }
    });
  }

  /**
   * Indicates whether the given project has at least one module that has the {@link AndroidGradleFacet}.
   *
   * @param project the given project.
   * @return {@code true} if the given project has at least one module that has the Android-Gradle facet, {@code false} otherwise.
   */
  public static boolean isGradleProject(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (Facets.getFirstFacet(module, AndroidGradleFacet.TYPE_ID) != null) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the current Gradle project. This method must be called in the event dispatch thread.
   *
   * @return the current Gradle project, or {@code null} if the current project is not a Gradle one or if there are no projects open.
   */
  @Nullable
  public static Project getCurrentGradleProject() {
    Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    boolean isGradleProject = project != null && isGradleProject(project);
    return isGradleProject ? project : null;
  }
}

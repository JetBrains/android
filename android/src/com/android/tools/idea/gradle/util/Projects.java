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

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

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
}

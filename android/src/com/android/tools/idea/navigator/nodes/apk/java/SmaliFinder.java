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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.util.io.FileUtil.join;

public class SmaliFinder {
  static final String EXT_SMALI = "smali";

  @NotNull private final Project myProject;

  SmaliFinder(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  VirtualFile findSmaliFile(@NotNull String classFqn) {
    File filePath = findSmaliFilePath(classFqn);
    if (filePath.isFile()) {
      return LocalFileSystem.getInstance().findFileByPath(filePath.getPath());
    }
    return null;
  }

  @NotNull
  File findSmaliFilePath(@NotNull String classFqn) {
    return new File(getDefaultSmaliOutputFolderPath(myProject), classFqn.replace('.', File.separatorChar) + ".smali");
  }

  @NotNull
  File findPackageFilePath(@NotNull String packageFqn) {
    File path = getDefaultSmaliOutputFolderPath(myProject);
    return new File(path, packageFqn.replace('.', File.separatorChar));
  }

  @NotNull
  public static File getDefaultSmaliOutputFolderPath(@NotNull Project project) {
    return new File(getBaseDirPath(project), join("smali", "out"));
  }
}

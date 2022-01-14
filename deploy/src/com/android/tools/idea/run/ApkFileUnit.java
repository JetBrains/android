/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run;

import java.io.File;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public class ApkFileUnit {
  @NotNull private final String myModuleName;
  @NotNull private final File myApkFile;

  public ApkFileUnit(@NotNull String moduleName, @NotNull File apkFile) {
    this.myModuleName = moduleName;
    this.myApkFile = apkFile;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public File getApkFile() {
    return myApkFile;
  }

  @NotNull
  public Path getApkPath() {
    return myApkFile.toPath();
  }
}

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
package com.android.tools.idea.gradle.project.sync.setup.module.common;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;

public class CompilerSettingsSetup {
  public void setOutputPaths(@NotNull ModifiableRootModel moduleModel, @NotNull File mainOutputPath, @Nullable File testOutputPath) {
    CompilerModuleExtension compilerSettings = moduleModel.getModuleExtension(CompilerModuleExtension.class);
    if (compilerSettings == null) {
      String msg = String.format("No compiler extension is found for module '%1$s'", moduleModel.getModule().getName());
      Logger.getInstance(getClass()).warn(msg);
      return;
    }
    compilerSettings.inheritCompilerOutputPath(false);
    if (!mainOutputPath.getPath().isEmpty()) {
      compilerSettings.setCompilerOutputPath(pathToIdeaUrl(mainOutputPath));
    }
    if (testOutputPath != null) {
      compilerSettings.setCompilerOutputPathForTests(pathToIdeaUrl(testOutputPath));
    }
  }
}

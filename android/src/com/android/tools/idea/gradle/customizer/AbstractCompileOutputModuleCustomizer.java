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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.util.FilePaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class AbstractCompileOutputModuleCustomizer<T> implements ModuleCustomizer<T> {
  private static final Logger LOG = Logger.getInstance(AbstractCompileOutputModuleCustomizer.class);

  protected void setOutputPaths(@NotNull Module module, @NotNull File mainDirPath, @Nullable File testDirPath) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel moduleSettings = moduleRootManager.getModifiableModel();
    CompilerModuleExtension compilerSettings = moduleSettings.getModuleExtension(CompilerModuleExtension.class);
    if (compilerSettings == null) {
      moduleSettings.dispose();
      LOG.warn(String.format("No compiler extension is found for module '%1$s'", module.getName()));
      return;
    }
    try {
      compilerSettings.inheritCompilerOutputPath(false);
      compilerSettings.setCompilerOutputPath(FilePaths.pathToIdeaUrl(mainDirPath));
      if (testDirPath != null) {
        compilerSettings.setCompilerOutputPathForTests(FilePaths.pathToIdeaUrl(testDirPath));
      }
    } finally {
      moduleSettings.commit();
    }
  }
}

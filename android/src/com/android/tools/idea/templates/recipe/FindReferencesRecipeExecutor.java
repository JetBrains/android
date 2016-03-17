/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates.recipe;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * {@link RecipeExecutor} that collects references as a result of
 * executing instructions in a {@link Recipe}.
 */
final class FindReferencesRecipeExecutor implements RecipeExecutor {
  private final RenderingContext myContext;

  public FindReferencesRecipeExecutor(@NotNull RenderingContext context) {
    myContext = context;
  }

  @Override
  public void copy(@NotNull File from, @NotNull File to) {
    if (from.isDirectory()) {
      throw new RuntimeException("Directories not supported for Find References");
    }
    addSourceFile(from);
    addTargetFile(to);
  }

  @Override
  public void instantiate(@NotNull File from, @NotNull File to) {
    addSourceFile(from);
    addTargetFile(to);
  }

  @Override
  public void merge(@NotNull File from, @NotNull File to) {
    addSourceFile(from);
    addTargetFile(to);
  }

  @Override
  public void append(@NotNull File from, @NotNull File to) {
    addSourceFile(from);
    addTargetFile(to);
  }

  @Override
  public void mkDir(@NotNull File at) {
  }

  @Override
  public void addFilesToOpen(@NotNull File file) {
    myContext.getFilesToOpen().add(resolveTargetFile(file));
  }

  @Override
  public void addDependency(@NotNull String mavenUrl) {
    myContext.getDependencies().add(mavenUrl);
  }

  @Override
  public void updateAndSyncGradle() {
  }

  @Override
  public void pushFolder(@NotNull String folder) {
  }

  @Override
  public void popFolder() {
  }

  public void addSourceFile(@NotNull File file) {
    myContext.getSourceFiles().add(resolveSourceFile(file));
  }

  public void addTargetFile(@NotNull File file) {
    myContext.getTargetFiles().add(resolveTargetFile(file));
  }

  private File resolveSourceFile(@NotNull File file) {
    if (file.isAbsolute()) {
      return file;
    }
    try {
      return myContext.getLoader().getSourceFile(file);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private File resolveTargetFile(@NotNull File file) {
    if (file.isAbsolute()) {
      return file;
    }
    return new File(myContext.getOutputRoot(), file.getPath());
  }
}

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

import com.android.tools.idea.templates.FreemarkerUtils.TemplatePostProcessor;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

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
  public void mkDir(@NotNull File at) {
  }

  @Override
  public void addFilesToOpen(@NotNull File file) {
    myContext.getFilesToOpen().add(file);
  }

  @Override
  public void addDependency(@NotNull String mavenUrl) {
    myContext.getDependencies().add(mavenUrl);
  }

  @Override
  public String processTemplate(@NotNull File recipe, @NotNull TemplatePostProcessor processor) {
    return null;
  }

  @Override
  public void updateAndSyncGradle() {
  }

  private void addSourceFile(@NotNull File file) {
    myContext.getSourceFiles().add(file);
  }

  private void addTargetFile(@NotNull File file) {
    myContext.getTargetFiles().add(file);
  }
}

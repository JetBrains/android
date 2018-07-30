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
package com.android.tools.idea.templates;

import com.android.tools.idea.templates.recipe.RecipeExecutor;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.command.WriteCommandAction;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TemplateRecipeTest {

  @Rule
  public final AndroidProjectRule projectRule = AndroidProjectRule.inMemory();

  @Rule
  public TemporaryFolder tmpFolderRule = new TemporaryFolder();

  @Test
  public void toolsBuildVersionInTemplates() {
    RecipeExecutor recipeExecutor = RenderingContext.Builder
      .newContext(tmpFolderRule.getRoot(), projectRule.getProject())
      .withOutputRoot(tmpFolderRule.getRoot())
      .withModuleRoot(tmpFolderRule.getRoot())
      .build()
      .getRecipeExecutor();

    WriteCommandAction.runWriteCommandAction(projectRule.getProject(), () -> {
      recipeExecutor.addDependency("testCompile", "junit:junit:4.12");
      recipeExecutor.updateAndSync();
    });
  }
}

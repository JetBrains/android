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
import com.google.common.truth.Truth;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.OutputStream;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

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

    runWriteCommandAction(projectRule.getProject(), () -> {
      recipeExecutor.addDependency("testCompile", "junit:junit:4.12");
      recipeExecutor.updateAndSync();
    });
  }

  @Test
  public void fileAlreadyExistWarning() throws Exception {
    RenderingContext renderingContext = RenderingContext.Builder
      .newContext(tmpFolderRule.getRoot(), projectRule.getProject())
      .withOutputRoot(tmpFolderRule.getRoot())
      .withModuleRoot(tmpFolderRule.getRoot())
      .build();

    runWriteCommandAction(projectRule.getProject(), (ThrowableComputable<Void, Exception>)() -> {
      VirtualFile vfFrom = projectRule.getProject().getBaseDir().findOrCreateChildData(this, "childFrom");
      File from = new File(vfFrom.getPath());

      VirtualFile vfTo = projectRule.getProject().getBaseDir().findOrCreateChildData(this, "childTo");
      File to = new File(vfTo.getPath());

      try (OutputStream os = vfFrom.getOutputStream(this)) {
          os.write('a');
        }

        try (OutputStream os = vfTo.getOutputStream(this)) {
          os.write('b');
        }

      renderingContext.getRecipeExecutor().instantiate(from, to);
      String[] warnings = renderingContext.getWarnings().toArray(new String[0]);

      Truth.assertThat(warnings).isNotEmpty();
      Truth.assertThat(warnings[0]).contains(to.getPath());

      return null;
    });
  }

  @Test
  public void addGlobalVariable() {
    final RenderingContext context = RenderingContext.Builder
      .newContext(tmpFolderRule.getRoot(), projectRule.getProject())
      .withOutputRoot(tmpFolderRule.getRoot())
      .withModuleRoot(tmpFolderRule.getRoot())
      .build();

    runWriteCommandAction(projectRule.getProject(), () -> {
      RecipeExecutor recipeExecutor = context.getRecipeExecutor();
      String stringKey = "stringKey";
      String value1 = "value1";
      String booleanKey = "booleanKey";
      String intKey = "intKey";
      recipeExecutor.addGlobalVariable(stringKey, value1);
      recipeExecutor.addGlobalVariable(booleanKey, true);
      recipeExecutor.addGlobalVariable(intKey, 1);

      Truth.assertThat(context.getParamMap().get(stringKey)).isEqualTo(value1);
      Truth.assertThat(context.getParamMap().get(booleanKey)).isEqualTo(true);
      Truth.assertThat(context.getParamMap().get(intKey)).isEqualTo(1);
    });
  }
}

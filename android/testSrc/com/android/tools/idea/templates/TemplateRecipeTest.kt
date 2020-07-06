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
package com.android.tools.idea.templates

import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TemplateRecipeTest {
  @Rule
  @JvmField
  val projectRule = AndroidProjectRule.inMemory()

  @Rule
  @JvmField
  var tmpFolderRule = TemporaryFolder()

  @Test
  fun toolsBuildVersionInTemplates() {
    val recipeExecutor = RenderingContext.Builder
      .newContext(tmpFolderRule.root, projectRule.project)
      .withOutputRoot(tmpFolderRule.root)
      .withModuleRoot(tmpFolderRule.root)
      .build()
      .recipeExecutor

    runWriteCommandAction(projectRule.project) {
      recipeExecutor.addDependency("testCompile", "junit:junit:4.12")
      recipeExecutor.updateAndSync()
    }
  }

  @Test
  @Throws(Exception::class)
  fun fileAlreadyExistWarning() {
    val renderingContext = RenderingContext.Builder
      .newContext(tmpFolderRule.root, projectRule.project)
      .withOutputRoot(tmpFolderRule.root)
      .withModuleRoot(tmpFolderRule.root)
      .build()

    runWriteCommandAction(projectRule.project) {
      val baseDir = projectRule.project.baseDir
      val vfFrom = baseDir.findOrCreateChildData(this, "childFrom")
      val from = File(vfFrom.path)

      val vfTo = baseDir.findOrCreateChildData(this, "childTo")
      val to = File(vfTo.path)

      vfFrom.getOutputStream(this).use { os -> os.write('a'.toInt()) }

      vfTo.getOutputStream(this).use { os -> os.write('b'.toInt()) }

      renderingContext.recipeExecutor.instantiate(from, to)
      val warnings = renderingContext.warnings.toTypedArray()

      Truth.assertThat(warnings).isNotEmpty()
      Truth.assertThat(warnings[0]).contains(to.path)
    }
  }

  @Test
  fun addGlobalVariable() {
    val context = RenderingContext.Builder
      .newContext(tmpFolderRule.root, projectRule.project)
      .withOutputRoot(tmpFolderRule.root)
      .withModuleRoot(tmpFolderRule.root)
      .build()

    runWriteCommandAction(projectRule.project) {
      val recipeExecutor = context.recipeExecutor
      val stringKey = "stringKey"
      val value1 = "value1"
      val booleanKey = "booleanKey"
      val intKey = "intKey"
      recipeExecutor.addGlobalVariable(stringKey, value1)
      recipeExecutor.addGlobalVariable(booleanKey, true)
      recipeExecutor.addGlobalVariable(intKey, 1)

      Truth.assertThat(context.paramMap[stringKey]).isEqualTo(value1)
      Truth.assertThat(context.paramMap[booleanKey]).isEqualTo(true)
      Truth.assertThat(context.paramMap[intKey]).isEqualTo(1)
    }
  }
}

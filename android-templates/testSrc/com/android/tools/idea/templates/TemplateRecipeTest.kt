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

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.google.common.truth.Truth
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TemplateRecipeTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  var tmpFolderRule = TemporaryFolder()

  @Test
  fun fileAlreadyExistWarning() {
    val mockProjectTemplateData = mock<ProjectTemplateData>()
    val mockModuleTemplateData = mock<ModuleTemplateData>()
    whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)

    val renderingContext = RenderingContext(
      projectRule.project,
      projectRule.module,
      "file already exists test",
      mockModuleTemplateData,
      tmpFolderRule.root,
      tmpFolderRule.root,
      true,
      true
    )

    runWriteCommandAction(projectRule.project) {
      val vfTo = projectRule.project.baseDir.findOrCreateChildData(this, "childTo")
      vfTo.getOutputStream(this).use { os -> os.write('b'.code) }

      val to = File(vfTo.path)
      val recipeExecutor = DefaultRecipeExecutor(renderingContext)
      recipeExecutor.save("something", to)
      val warnings = renderingContext.warnings.toTypedArray()

      Truth.assertThat(warnings).isNotEmpty()
      Truth.assertThat(warnings[0]).contains(to.path)
    }
  }
}

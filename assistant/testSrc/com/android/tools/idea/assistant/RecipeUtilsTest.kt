/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.assistant

import com.android.SdkConstants
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.TemplateData
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class RecipeUtilsTest {

  @get:Rule val projectRule = ProjectRule()

  @Test
  fun metadataDependenciesHaveImplementationAndApiConfigurations() {
    val dependencies: SetMultimap<String, String> = LinkedHashMultimap.create()
    val renderingContext = mock<RenderingContext>()
    whenever(renderingContext.dependencies).thenReturn(dependencies)
    dependencies.put(
      SdkConstants.GRADLE_IMPLEMENTATION_CONFIGURATION,
      "my.implementation.dependency",
    )
    dependencies.put(
      SdkConstants.GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION,
      "my.test.dependency",
    )
    dependencies.put(SdkConstants.GRADLE_API_CONFIGURATION, "my.api.dependency")

    val metadata = RecipeMetadata(mock(), mock())
    RecipeUtils.setUpMetadata(renderingContext, metadata)

    assertTrue(metadata.dependencies.contains("my.implementation.dependency"))
    assertTrue(metadata.dependencies.contains("my.api.dependency"))
    assertFalse(metadata.dependencies.contains("my.test.dependency"))
  }

  @Test
  fun getRecipeMetadata_returnsCachedMetadata() {
    val recipe = { _: RecipeExecutor, _: TemplateData -> }
    val project = projectRule.project
    val metadataFirst = RecipeUtils.getRecipeMetadata(recipe, project)
    val metadataSecond = RecipeUtils.getRecipeMetadata(recipe, project)

    assertEquals(metadataFirst, metadataSecond)
    assertTrue(RecipeUtils.getRecipeMetadataCacheSize() == 1)
  }

  @Test
  fun getRecipeMetadata_clearsCacheWhenProjectIsDisposed() {
    val recipe = { _: RecipeExecutor, _: TemplateData -> }
    val project = projectRule.project
    RecipeUtils.getRecipeMetadata(recipe, project)

    assertTrue(RecipeUtils.getRecipeMetadataCacheSize() > 0)

    runInEdtAndWait { ProjectManager.getInstance().closeAndDispose(project) }

    assertTrue(RecipeUtils.getRecipeMetadataCacheSize() == 0)
  }
}

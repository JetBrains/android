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
package com.android.tools.idea.wear.preview

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.preview.find.FilePreviewElementFinder
import com.android.tools.idea.testing.AndroidLibraryDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class WearTilePreviewRepresentationProviderTest {
  private val moduleWithTilesToolingPreviewDependency =
    AndroidModuleModelBuilder(
      gradlePath = ":with-dependency",
      selectedBuildVariant = "debug",
      createAndroidProjectBuilderForDefaultTestProjectStructure(IdeAndroidProjectType.PROJECT_TYPE_APP).withAndroidLibraryDependencyList {
        listOf(AndroidLibraryDependency.fromAddress("androidx.wear.tiles:tiles-tooling-preview:1.5.0"))
      },
    )
  private val moduleWithoutDependency =
    AndroidModuleModelBuilder(
      gradlePath = ":without-dependency",
      selectedBuildVariant = "debug",
      createAndroidProjectBuilderForDefaultTestProjectStructure(IdeAndroidProjectType.PROJECT_TYPE_APP),
    )

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      moduleWithTilesToolingPreviewDependency,
      moduleWithoutDependency,
    )

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  @Test
  // Regression test for b/477852888
  fun `accept calls preview finder only if androidx tiles-tooling-preview dependency is present`() =
    runBlocking<Unit> {
      val mockFinder = mock<FilePreviewElementFinder<PsiWearTilePreviewElement>>()
      val provider = WearTilePreviewRepresentationProvider(mockFinder)

      val fileWithTilesToolingPreviewDependency = fixture.addFileToProject("with-dependency/Test.kt", "")
      val fileWithoutDependency = fixture.addFileToProject("without-dependency/Other.kt", "")

      // Dependency missing: the finder should not be called
      provider.accept(project, fileWithoutDependency)
      verifyNoInteractions(mockFinder)

      // Dependency present: the finder should be called
      provider.accept(project, fileWithTilesToolingPreviewDependency)
      verify(mockFinder).hasPreviewElements(project, fileWithTilesToolingPreviewDependency.virtualFile)
    }
}

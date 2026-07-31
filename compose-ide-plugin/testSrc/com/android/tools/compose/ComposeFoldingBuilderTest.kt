/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose

import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test for [ComposeFoldingBuilder]. */
class ComposeFoldingBuilderTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixtureImpl by lazy { projectRule.fixture as CodeInsightTestFixtureImpl }

  private lateinit var testFile: PsiFile

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation()
    myFixture.addFileToProject(
      "src/${COMPOSE_UI_PACKAGE.replace(".", "/")}/Modifier.kt",
      // language=kotlin
      """
    package $COMPOSE_UI_PACKAGE

    interface Modifier {
      fun adjust():Modifier
      companion object : Modifier {
        fun adjust():Modifier {}
      }
    }
    """
        .trimIndent(),
    )

    // We can't use standard [myFixture.testFolding] because we need to properly load the file in the project so references can resolve.
    // We also can't use [myFixture.getFoldingDescription] because it hardcodes "quick" to true, and our resolution only should function
    // when not "quick".
    // Instead, we can set up the files and call into the folding builder directly to verify its returned descriptors.
    testFile =
      myFixture.loadNewFile(
        "src/com/example/Test.kt",
        // language=kotlin
        """
        package com.example

        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier

        @Composable
        fun HomeScreen() {
          val m = Modifier
            .adjust()
            .adjust()
        }
        """
          .trimIndent(),
      )
  }

  @Test
  fun `validate folding generated with quick = false`() {
    val composeFoldingBuilder = ComposeFoldingBuilder()
    val descriptors = runReadActionBlocking { composeFoldingBuilder.buildFoldRegions(testFile, testFile.fileDocument, /* quick= */ false) }
    assertThat(descriptors).hasLength(1)

    runReadActionBlocking {
      assertThat(descriptors[0].element.text).isEqualTo("Modifier\n    .adjust()\n    .adjust()")

      val placeholderText = composeFoldingBuilder.getPlaceholderText(descriptors[0].element, descriptors[0].range)
      assertThat(placeholderText).isEqualTo("Modifier.(...)")
    }
  }

  @Test
  fun `validate no foldings generated with quick = true`() {
    val descriptors = runReadActionBlocking { ComposeFoldingBuilder().buildFoldRegions(testFile, testFile.fileDocument, /* quick= */ true) }
    assertThat(descriptors).isEmpty()
  }
}

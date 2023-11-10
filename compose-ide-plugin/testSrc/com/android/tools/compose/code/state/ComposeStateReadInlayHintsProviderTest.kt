/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose.code.state

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubComposeRuntime
import org.jetbrains.android.compose.stubKotlinStdlib
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunsInEdt
@RunWith(JUnit4::class)
class ComposeStateReadInlayHintsProviderTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }
  private val provider = ComposeStateReadInlayHintsProvider()

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.stubComposableAnnotation()
    fixture.stubComposeRuntime()
    fixture.stubKotlinStdlib()
    StudioFlags.COMPOSE_STATE_READ_HIGHLIGHTING_ENABLED.override(true)
  }

  @Test
  fun createCollector_notKtFile() {
    val javaFile =
      fixture.addFileToProject(
        "com/example/Foo.java",
        // language=java
        """
      package com.example;
      class Foo {}
      """
          .trimIndent()
      )
    fixture.openFileInEditor(javaFile.virtualFile)

    assertThat(provider.createCollector(javaFile, fixture.editor)).isNull()
  }

  @Test
  fun createCollector_flagOff() {
    StudioFlags.COMPOSE_STATE_READ_HIGHLIGHTING_ENABLED.override(false)

    val kotlinFile =
      fixture.addFileToProject(
        "com/example/Foo.kt",
        // language=kotlin
        """
      package com.example
      class Foo
      """
          .trimIndent()
      )
    fixture.openFileInEditor(kotlinFile.virtualFile)

    assertThat(provider.createCollector(kotlinFile, fixture.editor)).isNull()
  }

  @Test
  fun createCollector() {
    val kotlinFile =
      fixture.addFileToProject(
        "com/example/Foo.kt",
        // language=kotlin
        """
      package com.example
      class Foo
      """
          .trimIndent()
      )
    fixture.openFileInEditor(kotlinFile.virtualFile)

    assertThat(provider.createCollector(kotlinFile, fixture.editor))
      .isSameAs(ComposeStateReadInlayHintsCollector)
  }
}

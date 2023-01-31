/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.compose.compiler.plugins.kotlin.ComposeFqNames
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.android.compose.stubComposableAnnotation
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests both [ComposeOverrideImplementsAnnotationsFilter] and that it's defined correctly in extension XML. */
@RunWith(JUnit4::class)
class ComposeOverrideImplementsAnnotationsFilterTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setup() {
    (projectRule.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    projectRule.fixture.stubComposableAnnotation(ComposeFqNames.Package.asString())
  }

  @Test
  fun composableAnnotationRetainedOnFunction() {
    val fixture = projectRule.fixture
    val file = fixture.loadNewFile(
      "src/com/example/Foo.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      interface Interface {
          @Composable
          fun Function()
      }

      class Impleme<caret>ntation : Interface {
      }
      """.trimIndent()
    )

    val intention = fixture.availableIntentions.singleOrNull { it.familyName == "Implement members" } ?: error("Intention not found")
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      intention.invoke(fixture.project, fixture.editor, file)
    }

    fixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      interface Interface {
          @Composable
          fun Function()
      }

      class Implementation : Interface {
          @Composable
          override fun Function() {
              TODO("Not yet implemented")
          }
      }
      """.trimIndent()
    )
  }

  @Test
  fun composableAnnotationRetainedOnArgument() {

    val fixture = projectRule.fixture
    val file = fixture.loadNewFile(
      "src/com/example/Foo.kt",
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      interface Interface {
          fun Function(argument: @Composable () -> Unit)
      }

      class Impleme<caret>ntation : Interface {
      }
      """.trimIndent()
    )

    val intention = fixture.availableIntentions.singleOrNull { it.familyName == "Implement members" } ?: error("Intention not found")
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      intention.invoke(fixture.project, fixture.editor, file)
    }

    fixture.checkResult(
      // language=kotlin
      """
      package com.example

      import androidx.compose.runtime.Composable

      interface Interface {
          fun Function(argument: @Composable () -> Unit)
      }

      class Implementation : Interface {
          override fun Function(argument: @Composable () -> Unit) {
              TODO("Not yet implemented")
          }
      }
      """.trimIndent()
    )
  }
}

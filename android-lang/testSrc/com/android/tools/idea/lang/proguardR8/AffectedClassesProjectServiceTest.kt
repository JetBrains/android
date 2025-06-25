/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.inspections.AffectedClassesProjectService
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.components.service
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class AffectedClassesProjectServiceTest {

  @get:Rule
  val projectRule = AndroidProjectRule
    .withAndroidModels(
      prepareProjectSources = { dir -> assertThat(File(dir, "src").mkdirs()).isTrue() },
      AndroidModuleModelBuilder(
        gradlePath = ":",
        selectedBuildVariant = "debug",
        projectBuilder = createAndroidProjectBuilderForDefaultTestProjectStructure()
      )
    )
    .onEdt()

  private fun fixture(): JavaCodeInsightTestFixture {
    return projectRule.fixture as JavaCodeInsightTestFixture
  }

  private fun affectedClassesProjectService(): AffectedClassesProjectService {
    return projectRule.project.service<AffectedClassesProjectService>()
  }

  @Before
  fun setUp() {
    fixture().addFileToProject(
      "src/com/packageA/X.java",
      """
        package com.packageA;

        class X {
          void methodX() {

          }
        }
    """.trimIndent()
    )

    fixture().addFileToProject(
      "src/com/packageA/XY.java",
      """
        package com.packageA;

        class XY {
          void methodXY() {

          }
        }
    """.trimIndent()
    )

    fixture().addFileToProject(
      "src/com/packageA/XYZ.java",
      """
        package com.packageA;

        class XYZ {
          void methodXYZ() {

          }
        }
    """.trimIndent()
    )

    fixture().addFileToProject(
      "src/com/packageB/A.java",
      """
        package com.packageB;

        class A {
          void methodA() {

          }
        }
    """.trimIndent()
    )

    fixture().addFileToProject(
      "src/com/packageB/AB.java",
      """
        package com.packageB;

        class AB {
          void methodAB() {

          }
        }
    """.trimIndent()
    )
  }

  @Test
  fun matchAllModuleSources() {
    val count = affectedClassesProjectService()
      .affectedClassesForQualifiedName(
        qualifiedPattern = "**.*"
      )
    assertThat(count).isEqualTo(/* expected = */ 5)
  }

  @Test
  fun matchPackageA() {
    val count = affectedClassesProjectService()
      .affectedClassesForQualifiedName(
        qualifiedPattern = "com.packageA.**"
      )
    assertThat(count).isEqualTo(/* expected = */ 3)
  }

  @Test
  fun matchPackageB() {
    val count = affectedClassesProjectService()
      .affectedClassesForQualifiedName(
        qualifiedPattern = "com.packageB.**"
      )
    assertThat(count).isEqualTo(/* expected = */ 2)
  }

  @Test
  fun matchPackageBWildCardPattern1() {
    val count = affectedClassesProjectService()
      .affectedClassesForQualifiedName(
        qualifiedPattern = "**.packageB.**"
      )
    assertThat(count).isEqualTo(/* expected = */ 2)
  }

  @Test
  fun matchPackageBWildCardsPattern2() {
    val count = affectedClassesProjectService()
      .affectedClassesForQualifiedName(
        qualifiedPattern = "*.packageB.**"
      )
    assertThat(count).isEqualTo(/* expected = */ 2)
  }

  @Test
  fun matchPackageBWildCardsPattern3() {
    val count = affectedClassesProjectService()
      .affectedClassesForQualifiedName(
        qualifiedPattern = "*.p*B.**"
      )
    assertThat(count).isEqualTo(/* expected = */ 2)
  }

  @Test
  fun matchAllModuleSourcesWildCardsPattern1() {
    val count = affectedClassesProjectService()
      .affectedClassesForQualifiedName(
        qualifiedPattern = "*.**.*"
      )
    assertThat(count).isEqualTo(/* expected = */ 5)
  }

}

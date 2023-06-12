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
package com.android.tools.idea.compose.annotator

import com.android.SdkConstants
import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Dependencies
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import kotlin.test.assertEquals
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val FILE_PATH = "src/main/Test.kt"

internal class SpringPickerLineMarkerProviderTest {
  private val rule = AndroidProjectRule.inMemory()

  @get:Rule
  val chain =
    RuleChain.outerRule(rule)
      .around(FlagRule(StudioFlags.COMPOSE_SPRING_PICKER, true))
      .around(EdtRule())!!

  private val fixture
    get() = rule.fixture

  @Before
  fun setup() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.registerLanguageExtensionPoint(
      LineMarkerProviders.getInstance(),
      SpringPickerLineMarkerProvider(),
      KotlinLanguage.INSTANCE
    )

    fixture.addFileToProject(
      FILE_PATH,
      // language=kotlin
      """
        import ${SdkConstants.PACKAGE_COMPOSE_ANIMATION}.spring
        import ${SdkConstants.PACKAGE_COMPOSE_ANIMATION}.SpringSpec
        import ${SdkConstants.PACKAGE_COMPOSE_ANIMATION}.FloatSpringSpec

        fun myFunction() {
          SpringSpec<Int>
          SpringSpec<Float>()
          spring<Int>
          spring<Float>()
          FloatSpringSpec()
        }
      """
        .trimIndent()
    )
  }

  @RunsInEdt
  @Test
  fun gutterIconOnSpringDeclarations() {
    Dependencies.add(rule.fixture, "compose/animation/animation-core")
    val psiFile = fixture.findPsiFile(FILE_PATH)
    val springLineMarkerInfos =
      LineMarkersPass.queryLineMarkers(psiFile, psiFile.viewProvider.document!!).filter {
        lineMarkerInfo ->
        lineMarkerInfo.lineMarkerTooltip == "SpringSpec configuration picker"
      }
    assertEquals(3, springLineMarkerInfos.size)
    assertEquals("SpringSpec<Float>()", springLineMarkerInfos[0].element!!.parent.parent.text)
    assertEquals("spring<Float>()", springLineMarkerInfos[1].element!!.parent.parent.text)
    assertEquals("FloatSpringSpec", springLineMarkerInfos[2].element!!.text)
  }
}

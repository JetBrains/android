/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.compose.ComposeExperimentalConfiguration
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.moveCaret
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(Parameterized::class)
internal class PreviewPickerAnnotationInspectionTest(previewAnnotationPackage: String, composableAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  private val composableAnnotationFqName = "$composableAnnotationPackage.Composable"
  private val previewToolingPackage = previewAnnotationPackage

  private val filePath = "src/main/Test.kt"

  @get:Rule
  val rule = ComposeProjectRule(
    previewAnnotationPackage = previewAnnotationPackage,
    composableAnnotationPackage = composableAnnotationPackage
  )


  private val fixture get() = rule.fixture

  private lateinit var psiFile: PsiFile

  @get:Rule
  val edtRule = EdtRule()

  @Before
  fun setup() {
    StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.override(true)
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    ComposeExperimentalConfiguration.getInstance().isPreviewPickerEnabled = true
    (rule.fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.enableInspections(PreviewPickerAnnotationInspection() as InspectionProfileEntry)

    psiFile = fixture.addFileToProject(
      filePath,
      // language=kotlin
      """
        import $composableAnnotationFqName
        import $previewToolingPackage.Preview

        @Preview(
          device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=480"
        )
        @Composable
        fun preview1() {}
      """.trimIndent())
    fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
  }

  @After
  fun teardown() {
    StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.clearOverride()
    StudioFlags.COMPOSE_EDITOR_SUPPORT.clearOverride()
  }

  @RunsInEdt
  @Test
  fun triggerErrorAndApplyFix() {
    // No existing errors
    assertNull(annotateAndGetLintInfo())

    fixture.moveCaret("unit=px,dpi=480|\"")
    fixture.backspace("px,dpi=480".count())
    fixture.type("sp")
    val info = annotateAndGetLintInfo()
    assertNotNull(info)
    assertEquals("spec:shape=Normal,width=1080,height=1920,unit=sp", info.text)
    assertEquals(
      """Bad value type for: unit.

Parameter: unit should be one of: px, dp.

Missing parameter: dpi.""",
      info.description
    )


    val fixAction = info.quickFixActionMarkers.first().first.action
    assertEquals("Replace with spec:shape=Normal,width=1080,height=1920,unit=px,dpi=480", fixAction.text)

    runUndoTransparentWriteAction {
      fixAction.invoke(fixture.project, fixture.editor, psiFile)
    }

    // There should be no more errors
    assertNull(annotateAndGetLintInfo())
  }

  private fun annotateAndGetLintInfo(): HighlightInfo? =
    fixture.doHighlighting().filter { it.severity == HighlightSeverity.ERROR }.let {
      assert(it.size <= 1)
      it.firstOrNull()
    }
}

private fun CodeInsightTestFixture.backspace(times: Int = 1) = type("\b".repeat(times))
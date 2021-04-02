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
package com.android.tools.idea.compose.preview.pickers

import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.namespaceVariations
import com.android.tools.idea.compose.preview.pickers.properties.PsiCallPropertyModel
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyItem
import com.android.tools.idea.compose.preview.pickers.properties.PsiPropertyModel
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private fun PreviewElement.annotationText(): String = ReadAction.compute<String, Throwable> {
  previewElementDefinitionPsi?.element?.text ?: ""
}

@RunWith(Parameterized::class)
class PsiPickerTests(previewAnnotationPackage: String, composableAnnotationPackage: String) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}.Preview {1}.Composable")
    val namespaces = namespaceVariations
  }

  private val COMPOSABLE_ANNOTATION_FQN = "$composableAnnotationPackage.Composable"
  private val PREVIEW_TOOLING_PACKAGE = previewAnnotationPackage

  @get:Rule
  val projectRule = ComposeProjectRule(previewAnnotationPackage = previewAnnotationPackage,
                                       composableAnnotationPackage = composableAnnotationPackage)

  @get:Rule
  val edtRule = EdtRule()
  private val fixture get() = projectRule.fixture
  private val project get() = projectRule.project

  @RunsInEdt
  @Test
  fun `the psi model reads the preview annotation correctly`() {
    @Language("kotlin")
    val fileContent = """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }

      @Composable
      @Preview("named")
      fun PreviewWithName() {
      }

      @Composable
      @Preview
      fun PreviewParameters() {
      }
      
      private const val nameFromConst = "Name from Const"
      
      @Composable
      @Preview(nameFromConst)
      fun PreviewWithNameFromConst() {
      }
    """.trimIndent()

    val file = fixture.configureByText("Test.kt", fileContent)
    val previews = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).toList()
    ReadAction.run<Throwable> {
      previews[0].also { noParametersPreview ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, noParametersPreview)
        assertNotNull(parsed.properties["", "name"])
        assertNull(parsed.properties.getOrNull("", "name2"))
      }
      previews[1].also { namedPreview ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, namedPreview)
        assertEquals("named", parsed.properties["", "name"].value)
      }
      previews[3].also { namedPreviewFromConst ->
        val parsed = PsiCallPropertyModel.fromPreviewElement(project, namedPreviewFromConst)
        assertEquals("Name from Const", parsed.properties["", "name"].value)
      }
    }
  }

  @RunsInEdt
  @Test
  fun `updating model updates the psi correctly`() {
    @Language("kotlin")
    val annotationWithParameters = """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    assertUpdatingModelUpdatesPsiCorrectly(annotationWithParameters)

    @Language("kotlin")
    val emptyAnnotation = """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview
      fun PreviewNoParameters() {
      }
      """.trimIndent()

    assertUpdatingModelUpdatesPsiCorrectly(emptyAnnotation)
  }

  @RunsInEdt
  @Test
  fun `supported parameters displayed correctly`() {
    @Language("kotlin")
    val fileContent = """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(name = "Test", fontScale = 1.2f, backgroundColor = 4294901760)
      fun PreviewWithParemeters() {
      }
    """.trimIndent()

    val file = fixture.configureByText("Test.kt", fileContent)
    val preview = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).first()
    val model = ReadAction.compute<PsiPropertyModel, Throwable> { PsiCallPropertyModel.fromPreviewElement(project, preview) }
    assertNotNull(model.properties["", "backgroundColor"].colorButton)
    assertEquals("1.20f", runReadAction { model.properties["", "fontScale"].value })
    assertEquals("0xFFFF0000", runReadAction { model.properties["", "backgroundColor"].value })

    model.properties["", "fontScale"].value = "0.5"
    model.properties["", "backgroundColor"].value = "0x00FF00"

    assertEquals("0.50f", runReadAction { model.properties["", "fontScale"].value })
    assertEquals("0x0000FF00", runReadAction { model.properties["", "backgroundColor"].value })
  }

  @RunsInEdt
  @Test
  fun `preview default values`() {
    @Language("kotlin")
    val fileContent = """
      import $COMPOSABLE_ANNOTATION_FQN
      import $PREVIEW_TOOLING_PACKAGE.Preview

      @Composable
      @Preview(name = "Test")
      fun PreviewNoParameters() {
      }
    """.trimIndent()

    val file = fixture.configureByText("Test.kt", fileContent)
    val preview = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).first()
    val model = ReadAction.compute<PsiPropertyModel, Throwable> { PsiCallPropertyModel.fromPreviewElement(project, preview) }
    assertEquals("1f", model.properties["", "fontScale"].defaultValue)
    assertEquals("false", model.properties["", "showBackground"].defaultValue)
    assertEquals("false", model.properties["", "showDecoration"].defaultValue)

    // Note that uiMode and device, are displayed through a ComboBox option and don't actually display these values
    assertEquals("0", model.properties["", "uiMode"].defaultValue)
    assertEquals("Default", model.properties["", "device"].defaultValue)

    // We hide the default value of some values when the value's behavior is undefined
    assertEquals(null, model.properties["", "widthDp"].defaultValue)
    assertEquals(null, model.properties["", "heightDp"].defaultValue)
    assertEquals(null, model.properties["", "apiLevel"].defaultValue)
    // We don't take the library's default value for color
    assertEquals(null, model.properties["", "backgroundColor"].defaultValue)
  }

  private fun assertUpdatingModelUpdatesPsiCorrectly(fileContent: String) {
    val file = fixture.configureByText("Test.kt", fileContent)
    val noParametersPreview = AnnotationFilePreviewElementFinder.findPreviewMethods(fixture.project, file.virtualFile).first()
    val model = ReadAction.compute<PsiPropertyModel, Throwable> { PsiCallPropertyModel.fromPreviewElement(project, noParametersPreview) }
    var expectedModificationsCountdown = 7
    model.addListener(object : PropertiesModelListener<PsiPropertyItem> {
      override fun propertyValuesChanged(model: PropertiesModel<PsiPropertyItem>) {
        expectedModificationsCountdown--
      }
    })

    model.properties["", "name"].value = "NoHello"
    // Try to override our previous write. Only the last one should persist
    model.properties["", "name"].value = "Hello"
    assertEquals("@Preview(name = \"Hello\")", noParametersPreview.annotationText())

    // Add other properties
    model.properties["", "group"].value = "Group2"
    model.properties["", "widthDp"].value = "32"
    assertEquals("Hello", model.properties["", "name"].value)
    assertEquals("Group2", model.properties["", "group"].value)
    assertEquals("32", model.properties["", "widthDp"].value)
    assertEquals("@Preview(name = \"Hello\", group = \"Group2\", widthDp = 32)", noParametersPreview.annotationText())

    // Set back to the default value
    model.properties["", "group"].value = null
    model.properties["", "widthDp"].value = null
    assertEquals("@Preview(name = \"Hello\")", noParametersPreview.annotationText())

    model.properties["", "name"].value = null
    try {
      model.properties["", "notexists"].value = "3"
      fail("Nonexistent property should throw NoSuchElementException")
    }
    catch (expected: NoSuchElementException) {
    }

    // Verify final values on model
    assertNull(model.properties["", "name"].value)
    assertNull(model.properties["", "group"].value)
    assertNull(model.properties["", "widthDp"].value)
    // Verify final state of file
    assertEquals("@Preview", noParametersPreview.annotationText())
    // Verify that every modification (setting, overwriting and deleting values) triggered the listener
    assertEquals(0, expectedModificationsCountdown)
  }
}
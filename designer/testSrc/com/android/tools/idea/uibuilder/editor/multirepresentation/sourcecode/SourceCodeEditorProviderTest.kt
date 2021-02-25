/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.editor.multirepresentation.TestPreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.TestPreviewRepresentationProvider
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jdom.Element

class SourceCodeEditorProviderTest : LightJavaCodeInsightFixtureTestCase(){

  lateinit var provider: SourceCodeEditorProvider

  override fun setUp() {
    super.setUp()

    provider = SourceCodeEditorProvider()
  }

  fun testOffIfDisabled() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(false)

    val file = myFixture.addFileToProject("src/Preview.kt", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }


  fun testAcceptsKotlinFile() {
    val file = myFixture.addFileToProject("src/Preview.kt", "")

    assertTrue(provider.accept(file.project, file.virtualFile))
  }

  fun testAcceptsJavaFile() {
    val file = myFixture.addFileToProject("src/Preview.java", "")

    assertTrue(provider.accept(file.project, file.virtualFile))
  }

  fun testDeclinesTxtFile() {
    val file = myFixture.addFileToProject("src/Preview.txt", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }

  fun testDeclinesXmlFile() {
    val file = myFixture.addFileToProject("src/Preview.xml", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }

  fun testCreatableForKotlinFile() {
    val file = myFixture.addFileToProject("src/Preview.kt", "")

    val editor = provider.createEditor(file.project, file.virtualFile)

    TestCase.assertNotNull(editor)

    provider.disposeEditor(editor)
  }

  fun testStateSerialization() {
    val file = myFixture.addFileToProject("src/Preview.kt", "")
    val representationWithState = object : TestPreviewRepresentation() {
      override fun getState(): PreviewRepresentationState? = mapOf(
        "key1" to "value1",
        "key2" to "value2"
      )
    }
    val serializationProvider = SourceCodeEditorProvider.forTesting(
      listOf(TestPreviewRepresentationProvider("Representation1", true),
             TestPreviewRepresentationProvider("Representation2", true, representationWithState))
    )
    val editor = serializationProvider.createEditor(file.project, file.virtualFile)
    // Editor are not selected in unit testing. Force the preview activation so it loads the state.
    (editor as TextEditorWithMultiRepresentationPreview<*>).preview.onActivate()
    try {
      val rootElement = Element("root")
      serializationProvider.writeState(editor.getState(FileEditorStateLevel.FULL), myFixture.project, rootElement)
      assertTrue(JDOMUtil.createOutputter("\n").outputString(rootElement).isNotBlank())
      val state = serializationProvider.readState(rootElement, myFixture.project, file.virtualFile) as SourceCodeEditorWithMultiRepresentationPreviewState

      assertContainsElements(state.previewState.representations.map { it.key }, "Representation1", "Representation2")
      val settings = state.previewState.representations.single { it.key == "Representation2" }.settings
      assertEquals("""
        key1 -> value1
        key2 -> value2
      """.trimIndent(), settings.map { "${it.key} -> ${it.value}" }.joinToString("\n"))
    } finally {
      Disposer.dispose(editor)
    }
  }

  fun testDumbModeUpdatesRepresentation() {
    val file = myFixture.addFileToProject("src/Preview.kt", "")
    val representation = TestPreviewRepresentationProvider("Representation1", false)
    val sourceCodeProvider = SourceCodeEditorProvider.forTesting(listOf(representation))
    val editor = sourceCodeProvider.createEditor(file.project, file.virtualFile).also {
      Disposer.register(myFixture.testRootDisposable, it)
    }
    val preview = (editor as TextEditorWithMultiRepresentationPreview<*>).preview

    assertThat(preview.representationNames).isEmpty()
    representation.isAccept = true
    assertThat(preview.representationNames).isEmpty()

    // Now trigger smart mode. Representations should update
    val dumbService = DumbServiceImpl.getInstance(project)
    dumbService.isDumb = true
    dumbService.isDumb = false
    assertThat(preview.representationNames).containsExactly("Representation1")
  }

  override fun tearDown() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.clearOverride()

    super.tearDown()
  }
}
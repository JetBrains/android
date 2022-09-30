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
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Facets
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.editor.multirepresentation.TestPreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.TestPreviewRepresentationProvider
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SourceCodeEditorProviderTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  lateinit var provider: SourceCodeEditorProvider

  @Before
  fun setUp() {
    provider = SourceCodeEditorProvider()
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.clearOverride()
  }

  @Test
  fun testOffIfDisabled() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(false)

    val file = fixture.addFileToProject("src/Preview.kt", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }

  @Test
  fun testOffIfNoAndroidModules() {
    runWriteActionAndWait {
      Facets.deleteAndroidFacetIfExists(fixture.module)
    }

    val file = fixture.addFileToProject("src/Preview.kt", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }

  @Test
  fun testAcceptsKotlinFile() {
    val file = fixture.addFileToProject("src/Preview.kt", "")

    assertTrue(provider.accept(file.project, file.virtualFile))
  }

  @Test
  fun testAcceptsJavaFile() {
    val file = fixture.addFileToProject("src/Preview.java", "")

    assertTrue(provider.accept(file.project, file.virtualFile))
  }

  @Test
  fun testDeclinesTxtFile() {
    val file = fixture.addFileToProject("src/Preview.txt", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }

  @Test
  fun testDeclinesXmlFile() {
    val file = fixture.addFileToProject("src/Preview.xml", "")

    assertFalse(provider.accept(file.project, file.virtualFile))
  }

  @Test
  fun testCreatableForKotlinFile() {
    val file = fixture.addFileToProject("src/Preview.kt", "")

    val editor = invokeAndWaitIfNeeded { provider.createEditor(file.project, file.virtualFile) }

    TestCase.assertNotNull(editor)

    invokeAndWaitIfNeeded {
      provider.disposeEditor(editor)
    }
  }

  @Test
  fun testStateSerialization() {
    val file = fixture.addFileToProject("src/Preview.kt", "")
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
    val editor = invokeAndWaitIfNeeded {
      return@invokeAndWaitIfNeeded serializationProvider.createEditor(file.project, file.virtualFile)
    } as TextEditorWithMultiRepresentationPreview<*>
    runBlocking {
      // Wait for the initializations
      editor.preview.onInit()
    }
    invokeAndWaitIfNeeded {
      // Editor are not selected in unit testing. Force the preview activation so it loads the state.
      editor.preview.onActivate()
      try {
        val rootElement = Element("root")
        serializationProvider.writeState(editor.getState(FileEditorStateLevel.FULL), fixture.project, rootElement)
        assertTrue(JDOMUtil.createOutputter("\n").outputString(rootElement).isNotBlank())
        val state = serializationProvider.readState(rootElement, fixture.project,
                                                    file.virtualFile) as SourceCodeEditorWithMultiRepresentationPreviewState

        assertContainsElements(state.previewState.representations.map { it.key }, "Representation1", "Representation2")
        val settings = state.previewState.representations.single { it.key == "Representation2" }.settings
        assertEquals("""
        key1 -> value1
        key2 -> value2
      """.trimIndent(), settings.map { "${it.key} -> ${it.value}" }.joinToString("\n"))
      }
      finally {
        Disposer.dispose(editor)
      }
    }
  }

  @Test
  fun testDumbModeUpdatesRepresentation() {
    val file = fixture.addFileToProject("src/Preview.kt", "")
    val representation = TestPreviewRepresentationProvider("Representation1", false)
    val sourceCodeProvider = SourceCodeEditorProvider.forTesting(listOf(representation))
    val editor = invokeAndWaitIfNeeded { sourceCodeProvider.createEditor(file.project, file.virtualFile) }.also {
      Disposer.register(fixture.testRootDisposable, it)
    }
    val preview = (editor as TextEditorWithMultiRepresentationPreview<*>).preview

    runBlocking {
      preview.awaitForRepresentationsUpdated()
    }

    assertThat(preview.representationNames).isEmpty()
    representation.isAccept = true
    assertThat(preview.representationNames).isEmpty()

    // Now trigger smart mode. Representations should update
    val dumbService = DumbServiceImpl.getInstance(projectRule.project)
    invokeAndWaitIfNeeded {
      dumbService.isDumb = true
      dumbService.isDumb = false
    }

    dumbService.waitForSmartMode()

    runBlocking {
      preview.awaitForRepresentationsUpdated()
    }
    assertThat(preview.representationNames).containsExactly("Representation1")
  }

  // Regression test for b/232045613
  @Test
  fun testDoesNotAcceptFilesBecauseOfTheExtension() {
    var type: FileType = KotlinFileType.INSTANCE
    val file = object : MockVirtualFile("Preview.kt") {
      override fun getFileType(): FileType = type
    }
    val representation = TestPreviewRepresentationProvider("Representation1", false)
    val sourceCodeProvider = SourceCodeEditorProvider.forTesting(listOf(representation))
    assertTrue(sourceCodeProvider.accept(project = projectRule.project, file))
    type = PlainTextFileType.INSTANCE
    assertFalse(sourceCodeProvider.accept(project = projectRule.project, file))
  }
}
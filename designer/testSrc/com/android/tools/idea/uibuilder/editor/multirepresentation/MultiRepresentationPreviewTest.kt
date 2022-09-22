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
package com.android.tools.idea.uibuilder.editor.multirepresentation

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.insertText
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertFalse

class MultiRepresentationPreviewTest {
  private lateinit var multiPreview: UpdatableMultiRepresentationPreview

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val project: Project get() = projectRule.project
  private val myFixture: CodeInsightTestFixture get() = projectRule.fixture

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
  }

  @After
  fun tearDown() {
    // MultiRepresentationPreview keeps a reference to a project, so it should get disposed before.
    Disposer.dispose(multiPreview)
  }

  private class UpdatableMultiRepresentationPreview(psiFile: PsiFile,
                                                    editor: Editor,
                                                    providers: List<PreviewRepresentationProvider>,
                                                    scope: CoroutineScope) :
    MultiRepresentationPreview(psiFile, editor, providers, scope) {

    val currentState: MultiRepresentationPreviewFileEditorState get() = getState(FileEditorStateLevel.FULL)
  }

  private suspend fun createMultiRepresentation(psiFile: PsiFile,
                                                editor: Editor,
                                                providers: List<PreviewRepresentationProvider>,
                                                initialState: MultiRepresentationPreviewFileEditorState? = MultiRepresentationPreviewFileEditorState()) =
    UpdatableMultiRepresentationPreview(psiFile, editor, providers, AndroidCoroutineScope(projectRule.testRootDisposable)).apply {
      onInit()
      // Simulate initial initialization by IntelliJ of the state loaded from disk
      initialState?.let { setStateAndUpdateRepresentations(it) }
      onActivate()
      awaitForRepresentationsUpdated()
    }

  @Test
  fun testNoProviders_noHistory() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(sampleFile, myFixture.editor, listOf())

    assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)

    multiPreview.updateRepresentations().join()

    assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)
  }

  @Test
  fun testNoProviders_someHistory() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(sampleFile,
                                             myFixture.editor,
                                             listOf(),
                                             MultiRepresentationPreviewFileEditorState("for"))

    multiPreview.updateRepresentations().join()

    assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)

    assertEquals("", multiPreview.currentState.selectedRepresentationName)
  }

  @Test
  fun testSingleAcceptingProvider_noHistory() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(TestPreviewRepresentationProvider("Accepting", true)))

    assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    assertEquals("Accepting", multiPreview.currentRepresentationName)
    assertEquals("Accepting", multiPreview.currentState.selectedRepresentationName)

    multiPreview.currentRepresentationName = "foo"

    assertNull(multiPreview.currentRepresentation)
    assertEquals("foo", multiPreview.currentRepresentationName)
    assertEquals("foo", multiPreview.currentState.selectedRepresentationName)


    multiPreview.currentRepresentationName = "Accepting"

    assertNotNull(multiPreview.currentRepresentation)
    assertEquals("Accepting", multiPreview.currentRepresentationName)
    assertEquals("Accepting", multiPreview.currentState.selectedRepresentationName)
  }

  @Test
  fun testSingleAcceptingProvider_validHistory() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(TestPreviewRepresentationProvider("Accepting", true)), MultiRepresentationPreviewFileEditorState("Accepting"))

    assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    assertEquals("Accepting", multiPreview.currentRepresentationName)

    multiPreview.currentRepresentationName = "Accepting"

    assertNotNull(multiPreview.currentRepresentation)
    assertEquals("Accepting", multiPreview.currentRepresentationName)
  }

  @Test
  fun testSingleAcceptingProvider_invalidHistory() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(TestPreviewRepresentationProvider("Accepting", true)), MultiRepresentationPreviewFileEditorState("Accepting"))

    assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    assertEquals("Accepting", multiPreview.currentRepresentationName)
    assertEquals("Accepting", multiPreview.currentState.selectedRepresentationName)
  }

  @Test
  fun testSingleNonAcceptingProvider_noHistory() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(TestPreviewRepresentationProvider("NonAccepting", false)))

    assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)

    multiPreview.updateRepresentations().join()

    assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)
    assertEquals("", multiPreview.currentState.selectedRepresentationName)
  }

  @Test
  fun testMultipleProviders_noHistory() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        TestPreviewRepresentationProvider("Accepting1", true),
        TestPreviewRepresentationProvider("Accepting2", true),
        TestPreviewRepresentationProvider("NonAccepting", false)))

    assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting1", "Accepting2")
    UsefulTestCase.assertDoesntContain(multiPreview.representationNames, "NonAccepting")
    assertEquals("Accepting1", multiPreview.currentRepresentationName)
    assertEquals("Accepting1", multiPreview.currentState.selectedRepresentationName)
    multiPreview.currentRepresentationName = "Accepting2"

    assertNotNull(multiPreview.currentRepresentation)
    assertEquals("Accepting2", multiPreview.currentRepresentationName)
    assertEquals("Accepting2", multiPreview.currentState.selectedRepresentationName)
  }

  @Test
  fun testMultipleProviders_validHistory() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        TestPreviewRepresentationProvider("Accepting1", true),
        TestPreviewRepresentationProvider("Accepting2", true),
        TestPreviewRepresentationProvider("NonAccepting", false)),
      MultiRepresentationPreviewFileEditorState("Accepting2"))

    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting1", "Accepting2")
    UsefulTestCase.assertDoesntContain(multiPreview.representationNames, "NonAccepting")
    assertEquals("Accepting2", multiPreview.currentRepresentationName)

    multiPreview.currentRepresentationName = "Accepting1"

    assertNotNull(multiPreview.currentRepresentation)
    assertEquals("Accepting1", multiPreview.currentRepresentationName)
    assertEquals("Accepting1", multiPreview.currentState.selectedRepresentationName)
  }

  @Test
  fun testMultipleProviders_conditionallyAccepting() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    val conditionallyAccepting = TestPreviewRepresentationProvider("ConditionallyAccepting", false)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        TestPreviewRepresentationProvider("Accepting", true),
        conditionallyAccepting),
      MultiRepresentationPreviewFileEditorState("ConditionallyAccepting"))

    // ConditionalAccepting is not available so it will not be restored by the surface load.
    assertEquals("Accepting", multiPreview.currentRepresentationName)

    assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    UsefulTestCase.assertDoesntContain(multiPreview.representationNames, "ConditionallyAccepting")
    assertEquals("Accepting", multiPreview.currentRepresentationName)
    assertEquals("Accepting", multiPreview.currentState.selectedRepresentationName)

    conditionallyAccepting.isAccept = true
    multiPreview.updateRepresentations().join()

    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting", "ConditionallyAccepting")

    multiPreview.currentRepresentationName = "ConditionallyAccepting"

    assertEquals("ConditionallyAccepting", multiPreview.currentRepresentationName)
    assertEquals("ConditionallyAccepting", multiPreview.currentState.selectedRepresentationName)

    conditionallyAccepting.isAccept = false
    multiPreview.updateRepresentations().join()

    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    UsefulTestCase.assertDoesntContain(multiPreview.representationNames, "ConditionallyAccepting")
    assertEquals("Accepting", multiPreview.currentRepresentationName)
    assertEquals("Accepting", multiPreview.currentState.selectedRepresentationName)
  }

  @Test
  fun testPreviewRepresentationShortcutsRegistered() = runBlocking {
    val shortcutsApplicableComponent = Mockito.mock(JComponent::class.java)

    val initiallyAcceptedRepresentation = Mockito.mock(PreviewRepresentation::class.java)
    whenever(initiallyAcceptedRepresentation.component).thenReturn(JPanel())
    val initiallyAcceptingProvider =
      TestPreviewRepresentationProvider("initialRepresentation", true, initiallyAcceptedRepresentation)

    val laterAcceptedRepresentation = Mockito.mock(PreviewRepresentation::class.java)
    whenever(laterAcceptedRepresentation.component).thenReturn(JPanel())
    val laterAcceptingProvider = TestPreviewRepresentationProvider("laterRepresentation", false, laterAcceptedRepresentation)

    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        initiallyAcceptingProvider,
        laterAcceptingProvider),
      MultiRepresentationPreviewFileEditorState("initialRepresentation"))
    multiPreview.updateRepresentations().join()

    Mockito.verify(initiallyAcceptedRepresentation, never()).registerShortcuts(any())
    Mockito.verify(laterAcceptedRepresentation, never()).registerShortcuts(any())

    multiPreview.registerShortcuts(shortcutsApplicableComponent)

    Mockito.verify(initiallyAcceptedRepresentation).registerShortcuts(shortcutsApplicableComponent)
    Mockito.verify(laterAcceptedRepresentation, never()).registerShortcuts(any())

    laterAcceptingProvider.isAccept = true
    multiPreview.updateRepresentations().join()

    Mockito.verify(initiallyAcceptedRepresentation).registerShortcuts(shortcutsApplicableComponent)
    Mockito.verify(laterAcceptedRepresentation).registerShortcuts(shortcutsApplicableComponent)
  }

  @Test
  fun testUpdateNotificationsPropagated() = runBlocking {
    val representation1 = Mockito.mock(PreviewRepresentation::class.java)
    whenever(representation1.component).thenReturn(JPanel())

    val representation2 = Mockito.mock(PreviewRepresentation::class.java)
    whenever(representation2.component).thenReturn(JPanel())

    val representation3 = Mockito.mock(PreviewRepresentation::class.java)
    whenever(representation3.component).thenReturn(JPanel())

    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        TestPreviewRepresentationProvider("Accepting1", true, representation1),
        TestPreviewRepresentationProvider("Accepting2", true, representation2),
        TestPreviewRepresentationProvider("NonAccepting", false, representation3)
      ))

    multiPreview.updateNotifications()

    Mockito.verify(representation1).updateNotifications(multiPreview)
    Mockito.verify(representation2).updateNotifications(multiPreview)
    Mockito.verify(representation3, never()).updateNotifications(any())
  }

  @Test
  fun testVerifyStateIsCorrectlyLoaded() = runBlocking {
    val state1 = mapOf("id" to "state1")
    val state3 = mapOf("id" to "state3")

    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)
    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        TestPreviewRepresentationProvider("Accepting1", true),
        TestPreviewRepresentationProvider("Accepting2", true),
        TestPreviewRepresentationProvider("Accepting3", true)),
      MultiRepresentationPreviewFileEditorState("Accepting2", listOf(
        Representation("Accepting1", state1),
        Representation("Accepting3", state3)
      )))
    multiPreview.updateRepresentations().join()
    assertNull((multiPreview.currentRepresentation as TestPreviewRepresentation).state)
    multiPreview.currentRepresentationName = "Accepting1"
    multiPreview.updateRepresentations().join()
    assertEquals(state1, (multiPreview.currentRepresentation as TestPreviewRepresentation).state)
    multiPreview.currentRepresentationName = "Accepting3"
    multiPreview.updateRepresentations().join()
    assertEquals(state3, (multiPreview.currentRepresentation as TestPreviewRepresentation).state)
  }

  @Test
  fun testActivationDeactivation() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)
    val representation1 = TestPreviewRepresentation()
    val representation2 = TestPreviewRepresentation()

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        TestPreviewRepresentationProvider("Representation1", true, representation1),
        TestPreviewRepresentationProvider("Representation2", true, representation2)
      ),
      MultiRepresentationPreviewFileEditorState("Representation1"))

    assertEquals(1, representation1.nActivations)
    assertEquals(0, representation2.nActivations)
    multiPreview.onActivate()
    // Call a second time to ensure that the call is not passed down to the representations.
    // Once the multi preview is active, subsequent onActivate calls are filtered out.
    multiPreview.onActivate()
    assertEquals(1, representation1.nActivations)
    assertEquals(0, representation2.nActivations)
    multiPreview.currentRepresentationName = "Representation2"
    multiPreview.updateRepresentations().join()
    // Previous representation should be de-activated, new one activated
    assertEquals(0, representation1.nActivations)
    assertEquals(1, representation2.nActivations)
    multiPreview.onDeactivate()
    // Make sure that calls after the first onDeactivate do not do anything.
    multiPreview.onDeactivate()
    multiPreview.onDeactivate()
    assertEquals(0, representation1.nActivations)
    assertEquals(0, representation2.nActivations)
  }

  @Test
  fun testMultiRepresentationReactivateHandlesRepresentationActivation() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)
    val representation1 = TestPreviewRepresentation()

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        TestPreviewRepresentationProvider("Representation1", true, representation1)
      ),
      MultiRepresentationPreviewFileEditorState("Representation1"))

    multiPreview.onActivate()
    assertEquals(1, representation1.nActivations)
    multiPreview.onDeactivate()
    assertEquals(0, representation1.nActivations)
    multiPreview.onActivate()
    assertEquals(1, representation1.nActivations)
  }

  @Test
  fun testCaretNotification() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", """
      // Line 1
      // Line 2
      // Line 3
      // Line 4
    """.trimIndent())
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)
    val representation1 = TestPreviewRepresentation()
    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(
        TestPreviewRepresentationProvider("Representation1", true, representation1)
      ),
      MultiRepresentationPreviewFileEditorState("Representation1"))
    multiPreview.updateRepresentations().join()

    withContext(uiThread) {
      assertEquals(0, representation1.nCaretNotifications)
      myFixture.editor.caretModel.moveCaretRelatively(0, 1, false, false, false)
      assertEquals(1, representation1.nCaretNotifications)
      myFixture.editor.caretModel.moveCaretRelatively(0, -1, false, false, false)
      assertEquals(2, representation1.nCaretNotifications)

      WriteCommandAction.runWriteCommandAction(project) {
        myFixture.editor.insertText("Hello world")
      }
      // insertText does not move the caret so we need to manually do it
      myFixture.editor.caretModel.moveCaretRelatively(11, 0, false, false, false)
      // No notification expected from a file modification
      assertEquals(2, representation1.nCaretNotifications)

      // This change will be picked up again
      myFixture.editor.caretModel.moveCaretRelatively(-11, 0, false, false, false)
      assertEquals(3, representation1.nCaretNotifications)

      multiPreview.onDeactivate()
      myFixture.editor.caretModel.moveCaretRelatively(0, 1, false, false, false)
      assertEquals(3, representation1.nCaretNotifications)
    }
  }

  @Test
  fun testRepresentationsUpdatedWhenDeactivated() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    val conditionalProvider = TestPreviewRepresentationProvider("Representation1", true)

    multiPreview = createMultiRepresentation(
      sampleFile,
      myFixture.editor,
      listOf(conditionalProvider),
      MultiRepresentationPreviewFileEditorState("Representation1"))

    multiPreview.onActivate()

    assertNotNull(multiPreview.currentRepresentation)
    assertNotEmpty(multiPreview.representationNames)

    // Emulate code change that removed representations and deactivates the preview
    conditionalProvider.isAccept = false
    multiPreview.updateRepresentations().join()
    multiPreview.onDeactivate()

    assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)

    // Emulate the code change that re-enables the preview
    conditionalProvider.isAccept = true
    multiPreview.updateRepresentations().join() // This could reactivate the preview

    assertNotNull(multiPreview.currentRepresentation)
    assertNotEmpty(multiPreview.representationNames)
  }

  // Regression test for http://b/176468484
  @Test
  fun testInitializationDuringDumbMode() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    invokeAndWaitIfNeeded {
      DumbServiceImpl.getInstance(project).isDumb = true
    }
    val provider = TestPreviewRepresentationProvider("Accepting", false)
    val futureMultiPreview = async {
      createMultiRepresentation(
        sampleFile,
        myFixture.editor,
        listOf(provider))
    }

    invokeAndWaitIfNeeded {
      provider.isAccept = true
      DumbServiceImpl.getInstance(project).isDumb = false
    }

    multiPreview = futureMultiPreview.await()
    multiPreview.awaitForRepresentationsUpdated()
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    assertEquals("Accepting", multiPreview.currentRepresentationName)
    assertEquals("Accepting", multiPreview.currentState.selectedRepresentationName)
  }

  @Ignore("http://b/240355870")
  @Test
  fun testRepresentationsAreDisposedInTime() = runBlocking {
    val sampleFile = myFixture.addFileToProject("src/Preview.kt", "")
    myFixture.configureFromExistingVirtualFile(sampleFile.virtualFile)

    val representations = mutableListOf<PreviewRepresentation>()
    val enabled = AtomicBoolean()

    val provider = object : PreviewRepresentationProvider {
      override val displayName = "foo"
      override suspend fun accept(project: Project, psiFile: PsiFile) = enabled.get()

      @Synchronized
      override fun createRepresentation(psiFile: PsiFile): PreviewRepresentation {
        val representation = TestPreviewRepresentation()
        representations.add(representation)
        return representation
      }
    }

    // TODO(b/236234873): Figure out why coroutines need this kind of warmup. Simply doing several [launch]s does not help.
    // The first time the coroutine code in updateRepresentations and setStateAndUpdateRepresentations is executed sequentially or with
    // a significant delay between each other. Therefore, we warm them up.
    createMultiRepresentation(sampleFile, myFixture.editor, listOf(), null).also {
      val job = it.updateRepresentations()
      it.setStateAndUpdateRepresentations(MultiRepresentationPreviewFileEditorState())
      job.join()
      Disposer.dispose(it)
    }

    multiPreview = createMultiRepresentation(sampleFile, myFixture.editor, listOf(provider), null)

    enabled.set(true)
    val job = multiPreview.updateRepresentations()
    multiPreview.setStateAndUpdateRepresentations(MultiRepresentationPreviewFileEditorState())
    job.join()

    assertEquals(1, representations.count { !Disposer.isDisposed(it) })
    assertNotNull(multiPreview.currentRepresentation)
    assertFalse(Disposer.isDisposed(multiPreview.currentRepresentation!!))
  }
}

fun <T> any(): T = Mockito.any<T>()
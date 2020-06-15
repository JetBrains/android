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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.mockito.ArgumentMatchers.endsWith
import org.mockito.AdditionalAnswers.returnsSecondArg
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import javax.swing.JComponent
import javax.swing.JPanel

class MultiRepresentationPreviewTest : LightJavaCodeInsightFixtureTestCase() {

  @Mock
  private lateinit var persistenceManager : PropertiesComponent

  private lateinit var multiPreview: UpdatableMultiRepresentationPreview

  override fun setUp() {
    super.setUp()

    MockitoAnnotations.initMocks(this)
  }

  override fun tearDown() {
    // MultiRepresentationPreview keeps a reference to a project, so it should get disposed before.
    Disposer.dispose(multiPreview)

    super.tearDown()
  }

  private class UpdatableMultiRepresentationPreview(psiFile: PsiFile,
                                                    providers: List<PreviewRepresentationProvider>,
                                                    persistenceManager: PropertiesComponent) :
    MultiRepresentationPreview(psiFile, providers, { persistenceManager }) {

    fun forceUpdateRepresentations() {
      super.updateRepresentations()
    }
  }

  private class DummyPreviewRepresentation : PreviewRepresentation {
    override val component = JPanel()
    override fun updateNotifications(parentEditor: FileEditor) { }
    override fun dispose() { }
  }

  private open class SimpleProvider(override val displayName: String, val isAccept: Boolean) : PreviewRepresentationProvider {
    override fun accept(project: Project, virtualFile: VirtualFile) = isAccept
    override fun createRepresentation(psiFile: PsiFile) : PreviewRepresentation = DummyPreviewRepresentation()
  }

  fun testNoProviders_noHistory() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).then(returnsSecondArg<String>())

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(dummyFile, listOf(), persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)

    Mockito.verify(persistenceManager, never()).setValue(anyString(), anyString())
  }

  fun testNoProviders_someHistory() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).thenReturn("foo")

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(dummyFile, listOf(), persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    TestCase.assertEquals("foo", multiPreview.currentRepresentationName)

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)

    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq(""))
  }

  fun testSingleAcceptingProvider_noHistory() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).then(returnsSecondArg<String>())

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile, listOf(SimpleProvider("Accepting", true)), persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    TestCase.assertEquals("Accepting", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq("Accepting"))

    multiPreview.currentRepresentationName = "foo"

    TestCase.assertNull(multiPreview.currentRepresentation)
    TestCase.assertEquals("foo", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq("foo"))


    multiPreview.currentRepresentationName = "Accepting"

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    TestCase.assertEquals("Accepting", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager, times(2)).setValue(endsWith("_selected"), eq("Accepting"))
  }

  fun testSingleAcceptingProvider_validHistory() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).thenReturn("Accepting")

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile, listOf(SimpleProvider("Accepting", true)), persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    TestCase.assertEquals("Accepting", multiPreview.currentRepresentationName)

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    TestCase.assertEquals("Accepting", multiPreview.currentRepresentationName)

    multiPreview.currentRepresentationName = "Accepting"

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    TestCase.assertEquals("Accepting", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager, never()).setValue(anyString(), anyString())
  }

  fun testSingleAcceptingProvider_invalidHistory() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).thenReturn("foo")

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile, listOf(SimpleProvider("Accepting", true)), persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    TestCase.assertEquals("foo", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager, never()).setValue(anyString(), anyString())

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    TestCase.assertEquals("Accepting", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq("Accepting"))
  }

  fun testSingleNonAcceptingProvider_noHistory() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).then(returnsSecondArg<String>())

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile, listOf(SimpleProvider("NonAccepting", false)), persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager, never()).setValue(anyString(), anyString())
  }

  fun testMultipleProviders_noHistory() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).then(returnsSecondArg<String>())

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile,
      listOf(
        SimpleProvider("Accepting1", true),
        SimpleProvider("Accepting2", true),
        SimpleProvider("NonAccepting", false)),
      persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    assertEmpty(multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager, never()).setValue(anyString(), anyString())

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting1", "Accepting2")
    UsefulTestCase.assertDoesntContain(multiPreview.representationNames, "NonAccepting")
    TestCase.assertEquals("Accepting1", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq("Accepting1"))

    multiPreview.currentRepresentationName = "Accepting2"

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    TestCase.assertEquals("Accepting2", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq("Accepting2"))
  }

  fun testMultipleProviders_validHistory() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).thenReturn("Accepting2")

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile,
      listOf(
        SimpleProvider("Accepting1", true),
        SimpleProvider("Accepting2", true),
        SimpleProvider("NonAccepting", false)),
      persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    TestCase.assertEquals("Accepting2", multiPreview.currentRepresentationName)

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting1", "Accepting2")
    UsefulTestCase.assertDoesntContain(multiPreview.representationNames, "NonAccepting")
    TestCase.assertEquals("Accepting2", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager, never()).setValue(anyString(), anyString())

    multiPreview.currentRepresentationName = "Accepting1"

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    TestCase.assertEquals("Accepting1", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq("Accepting1"))
  }

  fun testPreviewRepresentationShortcutsRegistered() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).thenReturn("initialRepresentation")

    val shortcutsApplicableComponent = Mockito.mock(JComponent::class.java)

    val initiallyAcceptedRepresentation = Mockito.mock(PreviewRepresentation::class.java)
    Mockito.`when`(initiallyAcceptedRepresentation.component).thenReturn(JPanel())
    val initiallyAcceptingProvider = object : SimpleProvider("initialRepresentation", true) {
      override fun createRepresentation(psiFile: PsiFile) = initiallyAcceptedRepresentation
    }

    val laterAcceptedRepresentation = Mockito.mock(PreviewRepresentation::class.java)
    Mockito.`when`(laterAcceptedRepresentation.component).thenReturn(JPanel())
    val laterAcceptingProvider = object : PreviewRepresentationProvider {
      var isAccept: Boolean = false
      override val displayName = "laterRepresentation"
      override fun accept(project: Project, virtualFile: VirtualFile) = isAccept
      override fun createRepresentation(psiFile: PsiFile) = laterAcceptedRepresentation
    }

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile,
      listOf(
        initiallyAcceptingProvider,
        laterAcceptingProvider),
      persistenceManager)
    multiPreview.forceUpdateRepresentations()

    Mockito.verify(initiallyAcceptedRepresentation, never()).registerShortcuts(any())
    Mockito.verify(laterAcceptedRepresentation, never()).registerShortcuts(any())

    multiPreview.registerShortcuts(shortcutsApplicableComponent)

    Mockito.verify(initiallyAcceptedRepresentation).registerShortcuts(shortcutsApplicableComponent)
    Mockito.verify(laterAcceptedRepresentation, never()).registerShortcuts(any())

    laterAcceptingProvider.isAccept = true
    multiPreview.forceUpdateRepresentations()

    Mockito.verify(initiallyAcceptedRepresentation).registerShortcuts(shortcutsApplicableComponent)
    Mockito.verify(laterAcceptedRepresentation).registerShortcuts(shortcutsApplicableComponent)
  }
}

fun <T> any(): T = Mockito.any<T>()
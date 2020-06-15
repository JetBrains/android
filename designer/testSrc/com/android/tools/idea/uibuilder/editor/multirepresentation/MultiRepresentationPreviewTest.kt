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
  private lateinit var persistenceManager: PropertiesComponent

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
    init {
      // Do the activation since this is not embedded within an actual editor.
      onActivate()
    }

    fun forceUpdateRepresentations() {
      super.updateRepresentations()
    }
  }

  private class DummyPreviewRepresentation : PreviewRepresentation {
    var nActivations = 0

    override val component = JPanel()
    override fun updateNotifications(parentEditor: FileEditor) {}
    override fun dispose() {}
    override fun onActivate() {
      nActivations++
    }

    override fun onDeactivate() {
      nActivations--
    }
  }

  private open class SimpleProvider(override val displayName: String, val isAccept: Boolean) : PreviewRepresentationProvider {
    override fun accept(project: Project, virtualFile: VirtualFile) = isAccept
    override fun createRepresentation(psiFile: PsiFile): PreviewRepresentation = DummyPreviewRepresentation()
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

  fun testMultipleProviders_conditionallyAccepting() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).thenReturn("ConditionallyAccepting")

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    val conditionallyAccepting = object : PreviewRepresentationProvider {
      var isAccept = false
      override val displayName = "ConditionallyAccepting"
      override fun accept(project: Project, virtualFile: VirtualFile) = isAccept
      override fun createRepresentation(psiFile: PsiFile) = DummyPreviewRepresentation()
    }

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile,
      listOf(
        SimpleProvider("Accepting", true),
        conditionallyAccepting),
      persistenceManager)

    TestCase.assertNull(multiPreview.currentRepresentation)
    assertEmpty(multiPreview.representationNames)
    TestCase.assertEquals("ConditionallyAccepting", multiPreview.currentRepresentationName)

    multiPreview.forceUpdateRepresentations()

    TestCase.assertNotNull(multiPreview.currentRepresentation)
    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    UsefulTestCase.assertDoesntContain(multiPreview.representationNames, "ConditionallyAccepting")
    TestCase.assertEquals("Accepting", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq("Accepting"))

    conditionallyAccepting.isAccept = true
    multiPreview.forceUpdateRepresentations()

    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting", "ConditionallyAccepting")

    multiPreview.currentRepresentationName = "ConditionallyAccepting"

    TestCase.assertEquals("ConditionallyAccepting", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager).setValue(endsWith("_selected"), eq("ConditionallyAccepting"))

    conditionallyAccepting.isAccept = false
    multiPreview.forceUpdateRepresentations()

    UsefulTestCase.assertContainsOrdered(multiPreview.representationNames, "Accepting")
    UsefulTestCase.assertDoesntContain(multiPreview.representationNames, "ConditionallyAccepting")
    TestCase.assertEquals("Accepting", multiPreview.currentRepresentationName)
    Mockito.verify(persistenceManager, times(2)).setValue(endsWith("_selected"), eq("Accepting"))
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

  fun testUpdateNotificationsPropagated() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).then(returnsSecondArg<String>())

    val representation1 = Mockito.mock(PreviewRepresentation::class.java)
    Mockito.`when`(representation1.component).thenReturn(JPanel())
    val acceptingProvider1 = object : PreviewRepresentationProvider {
      override val displayName = "Accepting1"
      override fun accept(project: Project, virtualFile: VirtualFile) = true
      override fun createRepresentation(psiFile: PsiFile) = representation1
    }

    val representation2 = Mockito.mock(PreviewRepresentation::class.java)
    Mockito.`when`(representation2.component).thenReturn(JPanel())
    val acceptingProvider2 = object : PreviewRepresentationProvider {
      override val displayName = "Accepting2"
      override fun accept(project: Project, virtualFile: VirtualFile) = true
      override fun createRepresentation(psiFile: PsiFile) = representation2
    }

    val representation3 = Mockito.mock(PreviewRepresentation::class.java)
    Mockito.`when`(representation3.component).thenReturn(JPanel())
    val nonAcceptingProvider = object : PreviewRepresentationProvider {
      override val displayName = "NonAccepting"
      override fun accept(project: Project, virtualFile: VirtualFile) = false
      override fun createRepresentation(psiFile: PsiFile) = representation3
    }

    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile, listOf(acceptingProvider1, acceptingProvider2, nonAcceptingProvider), persistenceManager)

    multiPreview.updateNotifications()

    Mockito.verify(representation1, never()).updateNotifications(any())
    Mockito.verify(representation2, never()).updateNotifications(any())
    Mockito.verify(representation3, never()).updateNotifications(any())

    multiPreview.forceUpdateRepresentations()

    multiPreview.updateNotifications()

    Mockito.verify(representation1).updateNotifications(multiPreview)
    Mockito.verify(representation2).updateNotifications(multiPreview)
    Mockito.verify(representation3, never()).updateNotifications(any())
  }

  fun testActivationDeactivation() {
    Mockito.`when`(persistenceManager.getValue(endsWith("_selected"), anyString())).thenReturn("Representation1")
    val dummyFile = myFixture.addFileToProject("src/Preview.kt", "")
    val representation1 = DummyPreviewRepresentation()
    val representation2 = DummyPreviewRepresentation()

    multiPreview = UpdatableMultiRepresentationPreview(
      dummyFile,
      listOf(
        object : SimpleProvider("Representation1", true) {
          override fun createRepresentation(psiFile: PsiFile): PreviewRepresentation = representation1
        },
        object : SimpleProvider("Representation2", true) {
          override fun createRepresentation(psiFile: PsiFile): PreviewRepresentation = representation2
        }),
      persistenceManager)
    multiPreview.forceUpdateRepresentations()

    assertEquals(1, representation1.nActivations)
    assertEquals(0, representation2.nActivations)
    multiPreview.onActivate()
    // Call a second time to ensure that the call is not passed down to the representations.
    // Once the multi preview is active, subsequent onActivate calls are filtered out.
    multiPreview.onActivate()
    assertEquals(1, representation1.nActivations)
    assertEquals(0, representation2.nActivations)
    multiPreview.currentRepresentationName = "Representation2"
    multiPreview.forceUpdateRepresentations()
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
}

fun <T> any(): T = Mockito.any<T>()
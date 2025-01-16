/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PsiPreviewElementInstance
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.TestSceneView
import com.android.tools.preview.ParametrizedComposePreviewElementInstance
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.rendering.RenderResult
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.TestActionEvent
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ViewInGalleryActionTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var dataContext: DataContext
  private val designSurface: NlDesignSurface = mock()
  private val modeManager: PreviewModeManager = mock()

  @Before
  fun setUp() {
    StudioFlags.VIEW_IN_GALLERY.override(true)
    dataContext =
      SimpleDataContext.builder()
        .add(DESIGN_SURFACE, designSurface)
        .add(PreviewModeManager.KEY, modeManager)
        .build()
  }

  @After
  fun tearDown() {
    StudioFlags.VIEW_IN_GALLERY.clearOverride()
  }

  @Test
  fun `selected sceneView can be opened to gallery`() {
    // We are in Default Mode
    whenever(modeManager.mode).thenReturn(MutableStateFlow(mock<PreviewMode.Default>()))

    // We are focussing a scene view
    val sceneManagerMock = createSceneManagerMock()
    whenever(designSurface.getSceneViewAt(3, 4)).thenReturn(TestSceneView(3, 4, sceneManagerMock))

    val viewInGalleryAction = ViewInGalleryAction(3, 4)
    val event = TestActionEvent.createTestEvent(viewInGalleryAction, dataContext)

    viewInGalleryAction.update(event)

    // Action should be visible.
    assertTrue(event.presentation.isVisible)
    // Action should be enabled.
    assertTrue(event.presentation.isEnabled)

    Disposer.register(projectRule.testRootDisposable, sceneManagerMock)
  }

  @Test
  fun `Cannot click on the action, we are in Gallery mode already`() {
    // We are in Gallery Mode.
    whenever(modeManager.mode).thenReturn(MutableStateFlow(mock<PreviewMode.Gallery>()))

    // We are focussing a scene view
    val sceneManagerMock = createSceneManagerMock()
    whenever(designSurface.getSceneViewAt(3, 4)).thenReturn(TestSceneView(3, 4, sceneManagerMock))

    val viewInGalleryAction = ViewInGalleryAction(3, 4)
    val event = TestActionEvent.createTestEvent(viewInGalleryAction, dataContext)

    viewInGalleryAction.update(event)

    // Action should be visible.
    assertTrue(event.presentation.isVisible)
    // Action should be disabled.
    assertFalse(event.presentation.isEnabled)

    Disposer.register(projectRule.testRootDisposable, sceneManagerMock)
  }

  @Test
  fun `Cannot click on the action, not clicking on any screen view`() {
    // We are in Default Mode.
    whenever(modeManager.mode).thenReturn(MutableStateFlow(mock<PreviewMode.Default>()))

    // We aren't clicking on any screen view.
    whenever(designSurface.getSceneViewAt(3, 4)).thenReturn(null)

    val viewInGalleryAction = ViewInGalleryAction(3, 4)
    val event = TestActionEvent.createTestEvent(viewInGalleryAction, dataContext)

    viewInGalleryAction.update(event)

    // Action should be visible.
    assertTrue(event.presentation.isVisible)
    // Action should be disabled.
    assertFalse(event.presentation.isEnabled)
  }

  @Test
  fun `Action not visible, Flag is disabled`() {
    StudioFlags.VIEW_IN_GALLERY.override(false)

    // We aren't focussing any screen view.
    whenever(designSurface.getSceneViewAt(3, 4)).thenReturn(null)

    val viewInGalleryAction = ViewInGalleryAction(3, 4)
    val event = TestActionEvent.createTestEvent(viewInGalleryAction, dataContext)

    viewInGalleryAction.update(event)

    // Action should not be visible.
    assertFalse(event.presentation.isVisible)

    StudioFlags.VIEW_IN_GALLERY.clearOverride()
  }

  // Regression test for b/385686357
  @Test
  fun `Action not visible, wrong preview mode`() {
    // We are in Ui Check Mode.
    whenever(modeManager.mode).thenReturn(MutableStateFlow(mock<PreviewMode.UiCheck>()))

    // We aren't focussing any screen view.
    whenever(designSurface.getSceneViewAt(3, 4)).thenReturn(null)

    val viewInGalleryAction = ViewInGalleryAction(3, 4)
    val event = TestActionEvent.createTestEvent(viewInGalleryAction, dataContext)

    viewInGalleryAction.update(event)

    // Action should not be visible.
    assertFalse(event.presentation.isVisible)
  }

  @Test
  fun `Cannot click on the action, not rendered yet`() {
    // We are in Default Mode.
    whenever(modeManager.mode).thenReturn(MutableStateFlow(mock<PreviewMode.Default>()))

    val sceneManagerMock = createSceneManagerMock(hasRendered = false)

    // We are focussing a scene view, but hasn't rendered yet.
    whenever(designSurface.getSceneViewAt(3, 4)).thenReturn(TestSceneView(3, 4, sceneManagerMock))

    val viewInGalleryAction = ViewInGalleryAction(3, 4)
    val event = TestActionEvent.createTestEvent(viewInGalleryAction, dataContext)

    viewInGalleryAction.update(event)

    // Action should be visible.
    assertTrue(event.presentation.isVisible)
    // Action should be disabled.
    assertFalse(event.presentation.isEnabled)

    Disposer.register(projectRule.testRootDisposable, sceneManagerMock)
  }

  @Test
  fun `selected preview opens to gallery correctly`() {
    val previewInstanceOfClickedSceneView = createSingleElementInstance("Right Clicked")
    val sceneManagerMock =
      createSceneManagerMock(previewElement = previewInstanceOfClickedSceneView)

    // We are right-clicking a scene view.
    whenever(designSurface.getSceneViewAt(3, 4)).thenReturn(TestSceneView(3, 4, sceneManagerMock))

    val viewInGalleryAction = ViewInGalleryAction(3, 4)
    val event = TestActionEvent.createTestEvent(viewInGalleryAction, dataContext)

    viewInGalleryAction.actionPerformed(event)

    // The Mode we are going to open is Gallery with the selected preview element.
    verify(modeManager).setMode(PreviewMode.Gallery(previewInstanceOfClickedSceneView))

    Disposer.register(projectRule.testRootDisposable, sceneManagerMock)
  }

  @Test
  fun `selected preview opens to gallery correctly with parametrized previews`() {
    // List of compose preview element.
    val parametrizedPreviewElements =
      ParametrizedComposePreviewElementInstance(
        basePreviewElement = createSingleElementInstance("instance"),
        parameterName = "param",
        providerClassFqn = "ProviderClass",
        index = 0,
        maxIndex = 4,
      )
    val sceneManagerMock = createSceneManagerMock(previewElement = parametrizedPreviewElements)

    // We are right-clicking a scene view.
    val rightClickedSceneView = TestSceneView(3, 4, sceneManagerMock)
    whenever(designSurface.getSceneViewAt(3, 4)).thenReturn(rightClickedSceneView)

    val viewInGalleryAction = ViewInGalleryAction(3, 4)
    val event = TestActionEvent.createTestEvent(viewInGalleryAction, dataContext)

    viewInGalleryAction.actionPerformed(event)

    // The Mode we are going to open is Gallery with the selected preview element.
    verify(modeManager).setMode(PreviewMode.Gallery(parametrizedPreviewElements))

    Disposer.register(projectRule.testRootDisposable, sceneManagerMock)
  }

  private fun createSceneManagerMock(
    hasRendered: Boolean = true,
    previewElement: PsiPreviewElementInstance? = null,
  ): LayoutlibSceneManager {
    val sceneManagerMock = mock<LayoutlibSceneManager>()

    val renderResult: RenderResult? = mock<RenderResult>().takeIf { hasRendered }
    whenever(sceneManagerMock.renderResult).thenReturn(renderResult)

    val modelMock = mock<NlModel>()
    whenever(sceneManagerMock.model).thenReturn(modelMock)

    val dataProvider =
      object : NlDataProvider(PREVIEW_ELEMENT_INSTANCE) {
        override fun getData(dataId: String): Any? =
          previewElement.takeIf { dataId == PREVIEW_ELEMENT_INSTANCE.name }
      }
    whenever(modelMock.dataProvider).thenReturn(dataProvider)
    return sceneManagerMock
  }

  private fun createSingleElementInstance(name: String) =
    SingleComposePreviewElementInstance<SmartPsiElementPointer<PsiElement>>(
      "composableMethodName",
      PreviewDisplaySettings(
        name = name,
        baseName = "A base name",
        parameterName = "paramName",
        group = null,
        showDecoration = false,
        showBackground = false,
        backgroundColor = null,
      ),
      null,
      null,
      PreviewConfiguration.cleanAndGet(),
    )
}

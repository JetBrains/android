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
package com.android.tools.idea

import com.android.tools.adtui.ZoomController
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.actions.LayoutPreviewHandler
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.TestPannable
import com.android.tools.idea.common.fixtures.ModelBuilder.TestActionManager
import com.android.tools.idea.common.model.DefaultSelectionModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.GuiInputHandler
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.TestInteractable
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.editor.NlActionManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.defaultActionHandlerProvider
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.util.concurrent.CompletableFuture
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

object DesignSurfaceTestUtil {

  @JvmStatic
  fun createMockSurface(
    disposableParent: Disposable,
    surfaceClass: Class<out DesignSurface<out SceneManager>>,
    interactionHandlerCreator: (DesignSurface<out SceneManager>) -> InteractionHandler,
  ): DesignSurface<out SceneManager> {
    val surface = Mockito.mock(surfaceClass)
    Disposer.register(disposableParent, surface)
    val listeners: MutableList<DesignSurfaceListener> = ArrayList()
    whenever(surface.uiDataSnapshot(any())).thenCallRealMethod()
    whenever(surface.layeredPane).thenReturn(JLayeredPane())
    val selectionModel: SelectionModel = DefaultSelectionModel()
    whenever(surface.selectionModel).thenReturn(selectionModel)
    whenever(surface.size).thenReturn(Dimension(1000, 1000))
    whenever(surface.zoomController).thenReturn(createZoomControllerFake(returnScale = 0.5))
    whenever(surface.selectionAsTransferable).thenCallRealMethod()
    val pannable = TestPannable()
    whenever(surface.pannable).thenReturn(pannable)
    val interactable = TestInteractable(pannable, JPanel(), surface)
    whenever(surface.guiInputHandler)
      .thenReturn(GuiInputHandler(surface, interactable, interactionHandlerCreator(surface)))
    if (surface is NlDesignSurface) {
      whenever(surface.analyticsManager).thenReturn(NlAnalyticsManager(surface))
      whenever(surface.actionManager).thenReturn(NlActionManager(surface))
      whenever(surface.layoutPreviewHandler)
        .thenReturn(
          object : LayoutPreviewHandler {
            override var previewWithToolsVisibilityAndPosition = true
          }
        )
    } else {
      whenever(surface.actionManager)
        .thenReturn(TestActionManager(surface as DesignSurface<SceneManager>))
    }
    Mockito.doAnswer { listeners.add(it.getArgument(0)) }.`when`(surface).addListener(any())

    Mockito.doAnswer { listeners.remove(it.getArgument<Any>(0) as DesignSurfaceListener) }
      .whenever(surface)
      .removeListener(any())

    selectionModel.addListener { _, selection ->
      listeners.forEach { listener -> listener.componentSelectionChanged(surface, selection) }
    }
    return surface
  }

  /**
   * FIXME(b/194482298): Refactor and remove this function. We don't need to give [SyncNlModel] when
   * creating [DesignSurface].
   */
  @JvmStatic
  fun createMockSurfaceWithModel(
    disposableParent: Disposable,
    project: Project,
    sceneManagerFactory: (DesignSurface<out SceneManager>, SyncNlModel) -> SceneManager,
    surfaceClass: Class<out DesignSurface<out SceneManager>>,
    interactionHandlerCreator: (DesignSurface<out SceneManager>) -> InteractionHandler,
    model: SyncNlModel,
  ): DesignSurface<out SceneManager> {
    val surface = createMockSurface(disposableParent, surfaceClass, interactionHandlerCreator)

    whenever(surface.model).thenReturn(model)
    whenever(surface.models).thenReturn(ImmutableList.of(model))
    whenever(surface.addModelWithoutRender(any()))
      .thenReturn(CompletableFuture.completedFuture<Nothing?>(null))
    whenever(surface.configurations).thenReturn(ImmutableList.of(model.configuration))

    // TODO: NlDesignSurface should not be referenced from here.
    // TODO: Do we need a special version of ModelBuilder for Nele?
    if (surface is NlDesignSurface) {
      whenever(surface.screenViewProvider).thenReturn(NlScreenViewProvider.BLUEPRINT)
      whenever(surface.actionHandlerProvider).thenReturn { defaultActionHandlerProvider(it) }
      whenever(surface.layoutPreviewHandler)
        .thenReturn(
          object : LayoutPreviewHandler {
            override var previewWithToolsVisibilityAndPosition = true
          }
        )
    }

    val sceneManager = sceneManagerFactory(surface, model)
    whenever(surface.getSceneManager(any())).thenReturn(sceneManager)
    whenever(surface.sceneManagers).thenReturn(ImmutableList.of(sceneManager))
    whenever(surface.getSceneViewAtOrPrimary(anyInt(), anyInt())).thenCallRealMethod()
    whenever(surface.focusedSceneView).thenReturn(sceneManager.sceneViews.firstOrNull())
    whenever(surface.sceneViews).thenReturn(sceneManager.sceneViews)
    val scene = sceneManager.scene
    sceneManager.update()
    whenever(surface.scene).thenReturn(scene)
    whenever(surface.project).thenReturn(project)
    whenever(surface.layoutType).thenCallRealMethod()
    val zoomController = createZoomControllerFake(returnScale = 0.5)
    whenever(surface.zoomController).thenReturn(zoomController)
    return surface
  }

  @JvmStatic
  @JvmOverloads
  fun createZoomControllerFake(
    returnScale: Double = 1.0,
    onZoom: ((ZoomType) -> Unit)? = null,
  ): ZoomController =
    object : ZoomController {
      override val scale: Double
        get() = returnScale

      override val screenScalingFactor: Double
        get() = 1.0

      override var storeId: String? = null

      override val minScale: Double
        get() = 0.1

      override val maxScale: Double
        get() = 10.0

      override fun setScale(scale: Double, x: Int, y: Int) = true

      override fun zoomToFit(): Boolean = true

      override fun getFitScale(): Double = 1.0

      override fun zoom(type: ZoomType): Boolean {
        onZoom?.invoke(type)
        return true
      }

      override fun canZoomIn(): Boolean = true

      override fun canZoomOut(): Boolean = true

      override fun canZoomToFit(): Boolean = true

      override fun canZoomToActual(): Boolean = true
    }

  fun setModelToSurfaceAndWait(surface: DesignSurface<*>, model: NlModel?) = runBlocking {
    val expectedModelChange = (model != null && surface.model != model)
    var modelChanged = false
    val surfaceListener =
      object : DesignSurfaceListener {
        override fun modelsChanged(surface: DesignSurface<*>, models: List<NlModel?>) {
          if (models.firstOrNull() === model) modelChanged = true
        }
      }
    surface.addListener(surfaceListener)
    surface.setModel(model)
    var attempts = 10
    while (!modelChanged && attempts > 0) {
      UIUtil.dispatchAllInvocationEvents()
      delay(1000)
      attempts--
    }
    assertEquals(expectedModelChange, modelChanged)
    surface.removeListener(surfaceListener)
  }
}

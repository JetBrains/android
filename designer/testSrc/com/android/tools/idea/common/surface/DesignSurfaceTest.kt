/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.RELATIVE_LAYOUT
import com.android.tools.adtui.ZoomController
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.scene.TestSceneManager
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.Dimension
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.event.ComponentEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.AndroidEditorSettings

class DesignSurfaceTest : LayoutTestCase() {

  private var originalSensitivity: Double = 0.0

  override fun setUp() {
    super.setUp()
    originalSensitivity = AndroidEditorSettings.getInstance().globalState.magnifySensitivity
  }

  override fun tearDown() {
    // Reset sensitivity
    AndroidEditorSettings.getInstance().globalState.magnifySensitivity = originalSensitivity
    super.tearDown()
  }

  fun testAddAndRemoveModel() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val model2 = model("model2.xml", component(CONSTRAINT_LAYOUT.oldName())).buildWithoutSurface()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    surface.addModelsWithoutRender(listOf(model1))
    assertEquals(1, surface.models.size)

    surface.addModelsWithoutRender(listOf(model2))
    assertEquals(2, surface.models.size)

    surface.removeModels(listOf(model2))
    surface.zoomController.zoomToFit()
    assertEquals(1, surface.models.size)

    surface.removeModels(listOf(model1))
    surface.zoomController.zoomToFit()
    assertEquals(0, surface.models.size)
  }

  fun testAddDuplicatedModel() {
    val model = model("model.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    surface.addModelsWithoutRender(listOf(model))
    assertEquals(1, surface.models.size)

    surface.addModelsWithoutRender(listOf(model))
    // should not add model again and the callback should not be triggered.
    assertEquals(1, surface.models.size)
  }

  fun testAddDuplicatedModelConcurrently() {
    val surface = TestDesignSurface(myModule.project, myModule.project)
    val model = model("model.xml", component(RELATIVE_LAYOUT)).build()
    runBlocking {
      launch {
        delay(Random.nextLong(100, 200))
        surface.addModelsWithoutRender(listOf(model))
      }
      launch {
        delay(Random.nextLong(100, 200))
        surface.addModelsWithoutRender(listOf(model))
      }
    }
    assertEquals("the same manager should be used for both models", listOf(model), surface.models)
  }

  fun testAddDifferentModelConcurrently() {
    val surface = TestDesignSurface(myModule.project, myModule.project)
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).build()
    val model2 = model("model2.xml", component(RELATIVE_LAYOUT)).build()
    runBlocking {
      launch {
        delay(Random.nextLong(100, 200))
        surface.addModelsWithoutRender(listOf(model1))
      }
      launch {
        delay(Random.nextLong(100, 200))
        surface.addModelsWithoutRender(listOf(model2))
      }
    }
    // Compare as sets because order is not guaranteed due to concurrent additions
    assertEquals(setOf(model1, model2), surface.models.toSet())
  }

  fun testSetDuplicatedModelConcurrently() {
    val surface = TestDesignSurface(myModule.project, myModule.project)

    val modelChangeCountDown = CountDownLatch(2)
    surface.addListener(
      object : DesignSurfaceListener {
        override fun modelsChanged(surface: DesignSurface<*>, models: List<NlModel?>) {
          modelChangeCountDown.countDown()
        }
      }
    )

    val model = model("model.xml", component(RELATIVE_LAYOUT)).build()
    surface.setModel(model)
    surface.setModel(model)

    modelChangeCountDown.await(2, TimeUnit.SECONDS)
    assertEquals(listOf(model), surface.models)
    assertFalse(model.isDisposed)
  }

  fun testSetDifferentModelConcurrently() {
    val surface = TestDesignSurface(myModule.project, myModule.project)

    val modelChangeCountDown = CountDownLatch(2)
    surface.addListener(
      object : DesignSurfaceListener {
        override fun modelsChanged(surface: DesignSurface<*>, models: List<NlModel?>) {
          modelChangeCountDown.countDown()
        }
      }
    )

    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).build()
    val model2 = model("model2.xml", component(RELATIVE_LAYOUT)).build()

    surface.setModel(model1)
    surface.setModel(model2)

    modelChangeCountDown.await(2, TimeUnit.SECONDS)
    assertEquals(1, surface.models.size)
  }

  fun testRemoveIllegalModel() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val model2 = model("model2.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val surface = TestDesignSurface(myModule.project, myModule.project)

    assertEquals(0, surface.models.size)

    surface.removeModels(listOf(model1))
    surface.zoomController.zoomToFit()
    // do nothing and the callback should not be triggered.
    assertEquals(0, surface.models.size)

    surface.addModelsWithoutRender(listOf(model1))
    assertEquals(1, surface.models.size)

    surface.removeModels(listOf(model2))
    surface.zoomController.zoomToFit()
    assertEquals(1, surface.models.size)
  }

  fun testScale() {
    val surface = TestDesignSurface(myModule.project, myModule.project)
    surface.zoomController.setScale(0.66, -1, -1)
    assertFalse(surface.zoomController.setScale(0.663, -1, -1))
    assertFalse(surface.zoomController.setScale(0.664, -1, -1))
    assertTrue(surface.zoomController.setScale(0.665, -1, -1))

    surface.zoomController.setScale(0.33, -1, -1)
    assertFalse(surface.zoomController.setScale(0.332, -1, -1))
    assertTrue(surface.zoomController.setScale(0.335, -1, -1))
  }

  fun testResizeSurfaceRebuildScene() {
    val builder =
      model(
        "relative.xml",
        component(RELATIVE_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight(),
      )
    val model1 = builder.buildWithoutSurface()
    val model2 = builder.buildWithoutSurface()

    val surface = TestDesignSurface(project, testRootDisposable)
    surface.addModelsWithoutRender(listOf(model1))
    surface.addModelsWithoutRender(listOf(model2))

    val scene1 = surface.getSceneManager(model1)!!.scene
    val scene2 = surface.getSceneManager(model2)!!.scene
    val oldVersion1 = scene1.displayListVersion
    val oldVersion2 = scene2.displayListVersion

    surface.dispatchEvent(ComponentEvent(surface, ComponentEvent.COMPONENT_RESIZED))

    assertFalse(scene1.displayListVersion == oldVersion1)
    assertFalse(scene2.displayListVersion == oldVersion2)
  }

  fun testResizeSurfaceDoesNotChangeScale() {
    // This also checks if the zoom level is same after resizing, because the screen factor of
    // TestDesignSurface is always 1.
    val builder =
      model(
        "relative.xml",
        component(RELATIVE_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight(),
      )
    val model1 = builder.buildWithoutSurface()
    val model2 = builder.buildWithoutSurface()

    val surface = TestDesignSurface(project, testRootDisposable)
    surface.addModelsWithoutRender(listOf(model1))
    surface.addModelsWithoutRender(listOf(model2))

    surface.setSize(1000, 1000)
    surface.dispatchEvent(ComponentEvent(surface, ComponentEvent.COMPONENT_RESIZED))
    val oldScale = surface.zoomController.scale

    surface.setSize(500, 500)
    surface.dispatchEvent(ComponentEvent(surface, ComponentEvent.COMPONENT_RESIZED))

    assertTrue(oldScale == surface.zoomController.scale)
  }

  fun testDesignSurfaceModelOrdering() {
    val builder =
      model(
        "relative.xml",
        component(RELATIVE_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight(),
      )
    val model1 = builder.buildWithoutSurface()
    val model2 = builder.buildWithoutSurface()
    val model3 = builder.buildWithoutSurface()

    val surface = TestDesignSurface(project, testRootDisposable)
    surface.addModelsWithoutRender(listOf(model1))
    surface.addModelsWithoutRender(listOf(model2))
    surface.addModelsWithoutRender(listOf(model3))

    assertThat(surface.models).containsExactly(model1, model2, model3).inOrder()
    surface.addModelsWithoutRender(listOf(model3))
    assertThat(surface.models).containsExactly(model1, model2, model3).inOrder()
    surface.addModelsWithoutRender(listOf(model1))
    assertThat(surface.models).containsExactly(model2, model3, model1).inOrder()
    surface.addModelsWithoutRender(listOf(model3))
    assertThat(surface.models).containsExactly(model2, model1, model3).inOrder()
  }

  fun testCanZoom() {
    val surface = TestDesignSurface(project, testRootDisposable)
    val zoomController = surface.zoomController

    // Test min
    zoomController.setScale(0.0104)
    assertFalse(zoomController.canZoomOut())
    zoomController.setScale(0.11)
    assertTrue(zoomController.canZoomOut())

    // Test max
    zoomController.setScale(9.996)
    assertFalse(zoomController.canZoomIn())
    zoomController.setScale(9.99)
    assertTrue(zoomController.canZoomIn())

    // Test some normal cases.
    zoomController.setScale(0.25)
    zoomController.canZoomIn()
    zoomController.canZoomOut()
    zoomController.setScale(0.5)
    zoomController.canZoomIn()
    zoomController.canZoomOut()
    zoomController.setScale(1.0)
    zoomController.canZoomIn()
    zoomController.canZoomOut()
    zoomController.setScale(2.0)
    zoomController.canZoomIn()
    zoomController.canZoomOut()
  }

  fun testSetScale() {
    val surface = TestDesignSurface(project, testRootDisposable)

    surface.zoomController.setScale(1.0)

    // Setting scale is restricted between min and max
    surface.zoomController.setScale(0.001)
    assertEquals(0.01, surface.zoomController.scale)
    surface.zoomController.setScale(20.0)
    assertEquals(10.0, surface.zoomController.scale)
  }

  fun testWaitDesignSurfaceResizeBeforeZoomToFit() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val fitScaleValue = 1.78

    val surface =
      TestDesignSurface(
        project = project,
        disposable = testRootDisposable,
        fitScaleProvider = { fitScaleValue },
      )
    surface.addModelsWithoutRender(listOf(model1))
    val zoomController = surface.zoomController as TestDesignSurfaceZoomController

    // We try to notify that we are ready to apply zoom-to-fit with a bitwiseNumber of "1"
    // (NOTIFY_ZOOM_TO_FIT_INT_MASK).
    zoomController.zoomToFit()
    zoomController.zoomToFit()
    zoomController.zoomToFit()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "2" (NOTIFY_COMPONENT_RESIZED_INT_MASK).
    assertEquals(1.0, surface.zoomController.scale)
    zoomController.notifyDesignSurfaceResized(surface.size.width, surface.size.height)

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "4" (NOTIFY_LAYOUT_CREATED).
    assertEquals(1.0, surface.zoomController.scale)
    zoomController.notifyLayoutCreatedForTest()

    // We check that zoom-to-fit has been applied.
    assertFalse(zoomController.zoomToFit())
  }

  fun testWaitRenderBeforeZoomToFit() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val fitScaleValue = 1.78

    val surface =
      TestDesignSurface(
        project = project,
        disposable = testRootDisposable,
        fitScaleProvider = { fitScaleValue },
      )
    surface.addModelsWithoutRender(listOf(model1))

    // We try to notify that we are ready to apply zoom-to-fit with a bitwiseNumber of
    // "2"(NOTIFY_COMPONENT_RESIZED_INT_MASK).
    // We try to call notifyComponentResizedForTest multiple times to make sure the mask doesn't
    // change its value if multiple resize callbacks happens.
    val zoomController = surface.zoomController as TestDesignSurfaceZoomController
    zoomController.notifyComponentResizedForTest()
    zoomController.notifyComponentResizedForTest()
    zoomController.notifyComponentResizedForTest()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "1" (NOTIFY_ZOOM_TO_FIT_INT_MASK).
    assertEquals(1.0, zoomController.scale)
    zoomController.zoomToFit()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "4" (NOTIFY_LAYOUT_CREATED).
    assertEquals(1.0, zoomController.scale)
    zoomController.notifyLayoutCreatedForTest()

    // We check that zoom-to-fit has been applied.
    assertFalse(zoomController.zoomToFit())
  }

  fun testOnlyResizeDoNotApplyZoomToFit() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val fitScaleValue = 1.78

    val surface =
      TestDesignSurface(
        project = project,
        disposable = testRootDisposable,
        fitScaleProvider = { fitScaleValue },
      )
    surface.addModelsWithoutRender(listOf(model1))

    val zoomController = surface.zoomController as TestDesignSurfaceZoomController
    zoomController.notifyLayoutCreatedForTest()

    // Zoom-to-fit shouldn't be applied if notifyZoomToFit doesn't have also a
    // bitwiseNumber of "2" (NOTIFY_COMPONENT_RESIZED_INT_MASK).
    assertEquals(1.0, surface.zoomController.scale)

    // We notify that we are ready to apply zoom-to-fit with a bitwiseNumber of "2"
    // (NOTIFY_COMPONENT_RESIZED_INT_MASK).
    // We try to call notifyComponentResizedForTest multiple times to make sure the mask doesn't
    // change its value if multiple resize callbacks happens.
    zoomController.notifyComponentResizedForTest()
    zoomController.notifyComponentResizedForTest()
    zoomController.notifyComponentResizedForTest()

    // We check that zoom-to-fit has been applied.
    assertTrue(surface.zoomController.zoomToFit())
  }

  fun testResetZoomToFitNotifier() {
    val model1 = model("model1.xml", component(RELATIVE_LAYOUT)).buildWithoutSurface()
    val fitScaleValue = 1.78

    val surface =
      TestDesignSurface(
          project = project,
          disposable = testRootDisposable,
          fitScaleProvider = { fitScaleValue },
        )
        .apply { this.setSize(200, 400) }
    surface.addModelsWithoutRender(listOf(model1))

    // Notify layout creation and DesignSurface resize
    val zoomController = surface.zoomController as TestDesignSurfaceZoomController
    zoomController.notifyLayoutCreatedForTest()
    zoomController.notifyComponentResizedForTest()

    // Notify zoom-to-fit a first time:
    // * Current mask takes the expected mask value
    // * Zoom-to-fit is applied
    zoomController.zoomToFit()
    assertFalse(zoomController.canZoomToFit())
    assertEquals(fitScaleValue, zoomController.scale)

    // Notify zoom-to-fit a second time current mask take ZOOM_TO_FIT_DONE_INT_MASK
    zoomController.zoomToFit()
    // zoomToFit returns false because zoom-to-fit is already applied.
    assertFalse(zoomController.zoomToFit())

    // Reset the zoom mask and change the zoom to a non zoom-to-fit value, we also wait for
    // DesignSurface resize.
    zoomController.resetZoomToFitSettings(shouldWaitForResize = true, surface.size)
    assertTrue(zoomController.setScale(0.45))

    // Simulate layout creations.
    zoomController.notifyLayoutCreatedForTest()

    // Scale is still not zoom-to-fit.
    assertEquals(0.45, zoomController.scale)

    // Simulate layout resize.
    zoomController.notifyComponentResizedForTest()

    // Scale is still not zoom-to-fit.
    assertEquals(0.45, zoomController.scale)

    // Notify zoom-to-fit a first time:
    // * Current mask takes the expected mask value
    // * Zoom-to-fit is applied
    zoomController.zoomToFit()
    assertFalse(zoomController.canZoomToFit())
    assertEquals(fitScaleValue, zoomController.scale)

    // Notify zoom-to-fit a second time current mask take ZOOM_TO_FIT_DONE_INT_MASK
    zoomController.zoomToFit()
    // zoomToFit returns false because zoom-to-fit is already applied.
    assertFalse(zoomController.zoomToFit())

    // Reset the zoom mask and change the zoom to a non zoom-to-fit value, we don't wait for
    // DesignSurface resize and the creation of DesignSurface layout creation
    zoomController.resetZoomToFitSettings(shouldWaitForResize = false, surface.size)
    assertTrue(zoomController.setScale(0.45))

    // Scale is still not zoom-to-fit.
    assertEquals(0.45, zoomController.scale)

    // Notify zoom-to-fit:
    // * Current mask takes the expected mask value
    // * Zoom-to-fit is applied
    zoomController.zoomToFit()
    assertFalse(zoomController.canZoomToFit())

    // Scale is now zoom-to-fit.
    assertEquals(fitScaleValue, zoomController.scale)
  }

  fun testRemoveModelRemovesOldSelections() {
    val model = model("model1.xml", component(RELATIVE_LAYOUT)).build()
    val surface = TestDesignSurface(project, testRootDisposable)
    surface.addModelsWithoutRender(listOf(model))
    surface.selectionModel.setSelection(listOf(model.getRoot()))
    assertEquals(model.getRoot(), surface.selectionModel.selection.first())
    surface.removeModels(listOf(model))
    assertTrue(surface.selectionModel.selection.isEmpty())
  }
}

class TestInteractionHandler(surface: DesignSurface<*>) : InteractionHandlerBase(surface) {
  override fun createInteractionOnPressed(
    mouseX: Int,
    mouseY: Int,
    modifiersEx: Int,
  ): Interaction? = null

  override fun createInteractionOnDrag(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? =
    null
}

class TestLayoutManager(scope: CoroutineScope) : PositionableContentLayoutManager(scope) {
  override fun layoutContainer(
    content: Collection<PositionableContent>,
    availableSize: Dimension,
  ) {}

  lateinit var surface: DesignSurface<*>

  override fun preferredLayoutSize(
    content: Collection<PositionableContent>,
    availableSize: Dimension,
  ): Dimension {
    return surface.sceneViews.map { it.getContentSize(null) }.firstOrNull() ?: Dimension(0, 0)
  }

  override fun getMeasuredPositionableContentPosition(
    content: Collection<PositionableContent>,
    availableWidth: Int,
    availableHeight: Int,
  ): Map<PositionableContent, Point> {
    return content.firstOrNull()?.let { mapOf(it to Point(0, 0)) } ?: emptyMap()
  }
}

class TestActionHandler<T : SceneManager>(surface: DesignSurface<T>) :
  DesignSurfaceActionHandler<DesignSurface<T>>(surface) {
  override val pasteTarget: NlComponent? = null

  override fun canHandleChildren(component: NlComponent, pasted: List<NlComponent>): Boolean = false

  override val flavor: DataFlavor = ItemTransferable.DESIGNER_FLAVOR

  override fun canDeleteElement(dataContext: DataContext): Boolean = false

  override fun isPasteEnabled(dataContext: DataContext): Boolean = false

  override fun isCopyEnabled(dataContext: DataContext): Boolean = false

  override fun isCopyVisible(dataContext: DataContext): Boolean = false

  override fun isCutVisible(dataContext: DataContext): Boolean = false

  override fun isPastePossible(dataContext: DataContext): Boolean = false
}

class TestDesignSurface(
  project: Project,
  disposable: Disposable,
  val createSceneManager: suspend (model: NlModel, surface: DesignSurface<*>) -> SceneManager =
    { model, surface ->
      TestSceneManager(model, surface)
    },
  testLayoutManager: TestLayoutManager = TestLayoutManager(disposable.createCoroutineScope()),
  fitScaleProvider: () -> Double = { 1.0 },
) :
  DesignSurface<SceneManager>(
    project = project,
    actionManagerProvider = { ModelBuilder.TestActionManager(it) },
    interactionProviderCreator = { TestInteractionHandler(it) },
    positionableLayoutManager = testLayoutManager,
    actionHandlerProvider = { TestActionHandler(it) },
    zoomControlsPolicy = ZoomControlsPolicy.VISIBLE,
  ) {

  init {
    testLayoutManager.surface = this
    Disposer.register(disposable, this)
  }

  override val layoutManagerSwitcher: LayoutManagerSwitcher?
    get() = null

  override val selectionAsTransferable: ItemTransferable
    get() = ItemTransferable(DnDTransferItem(0, ImmutableList.of()))

  override fun createSceneManager(model: NlModel) = runBlocking {
    createSceneManager(model, this@TestDesignSurface).apply { updateSceneViews() }
  }

  override fun scrollToCenter(list: List<NlComponent>) {}

  override val scrollToVisibleOffset = Dimension()

  override fun forceUserRequestedRefresh() {}

  override fun forceRefresh() {}

  override val selectableComponents: List<NlComponent>
    get() = emptyList()

  private val zoomControllerFake =
    createDesignSurfaceZoomControllerFake(
      project = project,
      disposable = disposable,
      trackZoom = null,
      fitScaleProvider = fitScaleProvider,
    )
  override val zoomController: ZoomController
    get() = zoomControllerFake
}

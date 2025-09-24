/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.SdkConstants
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.resources.Density
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.error.RenderIssueProvider.NlRenderIssueWrapper
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider.Companion.DEFAULT_SCREEN_MODE
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider.Companion.SCREEN_MODE_PROPERTY
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider.Companion.loadPreferredMode
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider.Companion.savePreferredMode
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder.Companion.build
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder.Companion.builder
import com.google.common.collect.ImmutableList
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import java.awt.Point
import java.util.stream.Collectors

class NlDesignSurfaceTest : LayoutTestCase() {
  private lateinit var designSurface: NlDesignSurface

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    designSurface = build(getProject(), getTestRootDisposable())
    designSurface.setSize(1000, 1000)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      Disposer.dispose(designSurface)
    } finally {
      super.tearDown()
    }
  }

  fun testScreenMode() {
    // Just in case, cleanup current preference to make testing environment consistence.
    PropertiesComponent.getInstance().unsetValue(SCREEN_MODE_PROPERTY)

    // Test the default behavior when there is no setting.
    assertEquals(loadPreferredMode(), DEFAULT_SCREEN_MODE)

    // Test the save and load functions
    val modes = NlScreenViewProvider.entries.toTypedArray()
    for (mode in modes) {
      savePreferredMode(mode)
      // The loaded mode should be same as the saved mode
      assertEquals(loadPreferredMode(), mode)
    }

    // Test next() function
    assertEquals(NlScreenViewProvider.BLUEPRINT, NlScreenViewProvider.RENDER.next())
    assertEquals(NlScreenViewProvider.RENDER_AND_BLUEPRINT, NlScreenViewProvider.BLUEPRINT.next())
    assertEquals(NlScreenViewProvider.RENDER, NlScreenViewProvider.RENDER_AND_BLUEPRINT.next())
  }

  fun ignore_testEmptyRenderSuccess() {
    val model: NlModel =
      model(
          "absolute.xml",
          component(SdkConstants.ABSOLUTE_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight(),
        )
        .build()
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on
    // the Material theme
    model.configuration.setTheme("android:Theme.NoTitleBar.Fullscreen")
    designSurface.setModel(model)

    refreshSurface()
    assertTrue(designSurface.getSceneManager(model)!!.renderResult!!.renderResult.isSuccess)
    assertFalse(
      designSurface.issueModel.issues.stream().anyMatch { issue: Issue? ->
        issue is NlRenderIssueWrapper && issue.severity === HighlightSeverity.ERROR
      }
    )
  }

  fun ignore_testRenderWhileBuilding() {
    val modelBuilder =
      model(
        "absolute.xml",
        component(SdkConstants.ABSOLUTE_LAYOUT)
          .withBounds(0, 0, 1000, 1000)
          .matchParentWidth()
          .matchParentHeight()
          .children(
            component("custom.view.not.present.yet")
              .withBounds(100, 100, 100, 100)
              .matchParentWidth()
              .matchParentHeight()
          ),
      )

    var model: NlModel = modelBuilder.build()
    // Simulate that we are in the middle of a build
    //    BuildSettings.getInstance(getProject()).setBuildMode(BuildMode.SOURCE_GEN);
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on
    // the Material theme
    model.configuration.setTheme("android:Theme.NoTitleBar.Fullscreen")
    designSurface.setModel(model)

    refreshSurface()

    // Now finish the build, and try to build again. The "project is still building" should be gone.
    //    BuildSettings.getInstance(getProject()).setBuildMode(null);
    model = modelBuilder.build()
    model.configuration.setTheme("android:Theme.NoTitleBar.Fullscreen")
    designSurface.setModel(model)

    refreshSurface()
    // Because there is a missing view, some other extra errors will be generated about missing
    // styles. This is caused by
    // MockView (which is based on TextView) that depends on some Material styles.
    // We only care about the missing class error.
    assertTrue(
      designSurface.issueModel.issues.stream().anyMatch { issue: Issue? ->
        issue!!.summary.startsWith("Missing classes")
      }
    )
    assertFalse(
      designSurface.issueModel.issues.stream().anyMatch { issue: Issue? ->
        issue!!.summary.startsWith("The project is still building")
      }
    )
  }

  /**
   * Copy a component and check that the id of the new component has the same base and an
   * incremented number
   */
  fun ignore_testCopyPasteWithId() {
    val model: NlModel =
      model(
          "my_linear.xml",
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 0, 200, 200)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp")
            ),
        )
        .build()
    designSurface.setModel(model)
    val handler: DesignSurfaceActionHandler<*> = NlDesignSurfaceActionHandler(designSurface!!)
    val dataContext = DataContext.EMPTY_CONTEXT
    val button = model.treeReader.find("cuteLittleButton")
    designSurface.selectionModel.setSelection(listOfNotNull(button))
    handler.performCopy(dataContext)
    handler.performPaste(dataContext)
    val button2 = model.treeReader.find("cuteLittleButton2")
    assertNotNull(button2)
    designSurface.selectionModel.setSelection(listOfNotNull(button2))
    handler.performCopy(dataContext)
    handler.performPaste(dataContext)
    val button3 = model.treeReader.find("cuteLittleButton3")
    assertNotNull(button3)
  }

  /** Cut a component and check that the id of the new component has been conserved */
  fun ignore_testCutPasteWithId() {
    val model: NlModel =
      model(
          "my_linear.xml",
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 0, 200, 200)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp")
            ),
        )
        .build()
    designSurface.setModel(model)
    val handler: DesignSurfaceActionHandler<*> = NlDesignSurfaceActionHandler(designSurface!!)
    val dataContext = DataContext.EMPTY_CONTEXT
    val button = model.treeReader.find("cuteLittleButton")
    designSurface.selectionModel.setSelection(listOfNotNull(button))
    handler.performCut(dataContext)
    handler.performPaste(dataContext)
    assertComponentWithId(model, "cuteLittleButton")
  }

  /** Cut a component and check that the id of the new component has been conserved */
  fun testMultipleCutPasteWithId() {
    val model: NlModel =
      model(
          "my_linear.xml",
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 0, 200, 200)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp"),
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton2")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp"),
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton3")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp"),
            ),
        )
        .build()
    designSurface.setModel(model)
    val handler: DesignSurfaceActionHandler<*> = NlDesignSurfaceActionHandler(designSurface)
    val dataContext = DataContext.EMPTY_CONTEXT
    val button =
      model.treeReader.find("cuteLittleButton")
        ?: throw NullPointerException("Button should not be null")
    val button2 =
      model.treeReader.find("cuteLittleButton2")
        ?: throw NullPointerException("Button2 should not be null")
    val button3 =
      model.treeReader.find("cuteLittleButton3")
        ?: throw NullPointerException("Button3 should not be null")
    designSurface.selectionModel.setSelection(listOf(button, button2, button3))
    handler.performCut(dataContext)
    handler.performPaste(dataContext)
    assertComponentWithId(model, "cuteLittleButton")
    assertComponentWithId(model, "cuteLittleButton2")
    assertComponentWithId(model, "cuteLittleButton3")
  }

  /** Cut a component and check that the id of the new component has been conserved */
  fun ignore_testMultipleCopyPasteWithId() {
    val model: NlModel =
      model(
          "my_linear.xml",
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 0, 200, 200)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp"),
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton2")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp"),
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton3")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp"),
            ),
        )
        .build()
    designSurface.setModel(model)
    val handler: DesignSurfaceActionHandler<*> = NlDesignSurfaceActionHandler(designSurface)
    val dataContext = DataContext.EMPTY_CONTEXT
    val button =
      model.treeReader.find("cuteLittleButton")
        ?: throw NullPointerException("Button should not be null")
    val button2 =
      model.treeReader.find("cuteLittleButton2")
        ?: throw NullPointerException("Button2 should not be null")
    val button3 =
      model.treeReader.find("cuteLittleButton3")
        ?: throw NullPointerException("Button3 should not be null")
    designSurface.selectionModel.setSelection(listOf(button, button2, button3))
    handler.performCopy(dataContext)
    designSurface.selectionModel.clear()
    handler.performPaste(dataContext)
    assertComponentWithId(model, "cuteLittleButton4")
    assertComponentWithId(model, "cuteLittleButton5")
    assertComponentWithId(model, "cuteLittleButton6")
  }

  fun ignore_testCutThenCopyWithId() {
    val model: NlModel =
      model(
          "my_linear.xml",
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 0, 200, 200)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp")
            ),
        )
        .build()
    designSurface.setModel(model)
    val handler: DesignSurfaceActionHandler<*> = NlDesignSurfaceActionHandler(designSurface)
    val dataContext = DataContext.EMPTY_CONTEXT
    val button =
      model.treeReader.find("cuteLittleButton")
        ?: throw NullPointerException("Button should not be null")
    designSurface.selectionModel.setSelection(listOf(button))
    handler.performCut(dataContext)
    handler.performPaste(dataContext)
    val button2 =
      model.treeReader.find("cuteLittleButton")
        ?: throw NullPointerException("Button should not be null")
    assertNotNull("Component should have been pasted with the id cuteLittleButton", button2)

    designSurface.selectionModel.setSelection(listOf(button2))
    handler.performCopy(dataContext)
    handler.performPaste(dataContext)
    assertComponentWithId(model, "cuteLittleButton2")
  }

  /**
   * Cut component1, paste it, copy it, cut the copy and paste it. The copy should keep the same id
   * as the first time.
   */
  fun ignore_testCutPasteCut() {
    val model: NlModel =
      model(
          "my_linear.xml",
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 0, 200, 200)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.BUTTON)
                .id("@+id/cuteLittleButton")
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp")
            ),
        )
        .build()
    designSurface.setModel(model)
    val handler: DesignSurfaceActionHandler<*> = NlDesignSurfaceActionHandler(designSurface!!)
    val dataContext = DataContext.EMPTY_CONTEXT
    val button =
      model.treeReader.find("cuteLittleButton")
        ?: throw NullPointerException("Button should not be null")
    designSurface.selectionModel.setSelection(listOf(button))
    handler.performCut(dataContext)
    handler.performPaste(dataContext)
    val buttonCut =
      model.treeReader.find("cuteLittleButton")
        ?: throw NullPointerException("Button should not be null")
    assertNotNull("Component should have been pasted with the id cuteLittleButton", buttonCut)

    designSurface.selectionModel.setSelection(listOf(buttonCut))
    handler.performCopy(dataContext)
    handler.performPaste(dataContext)
    assertComponentWithId(model, "cuteLittleButton2")

    val buttonCopied =
      model.treeReader.find("cuteLittleButton2")
        ?: throw NullPointerException("Button should not be null")
    designSurface.selectionModel.setSelection(listOf(buttonCopied))
    handler.performCut(dataContext)
    handler.performPaste(dataContext)
    handler.performPaste(dataContext)
    assertNull(model.treeReader.find("cuteLittleButton4"))
    assertComponentWithId(model, "cuteLittleButton2")
  }

  fun ignore_testZoom() =
    kotlinx.coroutines.test.runTest {
      val model =
        model(
            "my_linear.xml",
            component(SdkConstants.LINEAR_LAYOUT)
              .withBounds(0, 0, 200, 200)
              .matchParentWidth()
              .matchParentHeight()
              .children(
                component(SdkConstants.FRAME_LAYOUT)
                  .withBounds(100, 100, 100, 100)
                  .width("100dp")
                  .height("100dp")
              ),
          )
          .build()
      designSurface.setModel(model)
      designSurface.setScrollViewSizeAndValidateForTest(1000, 1000)
      // First zoom is zoom-to-fit.
      val origScale = designSurface.zoomController.scale
      assertFalse(designSurface.zoomController.canZoomToFit())

      val view = designSurface.focusedSceneView
      assertEquals(
        Point(-122, -122),
        Coordinates.getAndroidCoordinate(view!!, designSurface.pannable.scrollPosition),
      )

      designSurface.zoomController.zoom(ZoomType.IN)
      var scale = designSurface.zoomController.scale
      assertTrue(scale > origScale)
      assertEquals(
        Point(8, 8),
        Coordinates.getAndroidCoordinate(view, designSurface.pannable.scrollPosition),
      )

      designSurface.zoomController.zoom(ZoomType.IN, 100, 100)
      assertTrue(designSurface.zoomController.scale > scale)
      assertEquals(
        Point(12, 12),
        Coordinates.getAndroidCoordinate(view, designSurface.pannable.scrollPosition),
      )

      designSurface.zoomController.zoom(ZoomType.OUT, 100, 100)
      assertEquals(
        Point(7, 7),
        Coordinates.getAndroidCoordinate(view, designSurface.pannable.scrollPosition),
      )
      designSurface.zoomController.zoom(ZoomType.OUT)
      assertEquals(
        Point(-122, -122),
        Coordinates.getAndroidCoordinate(view, designSurface.pannable.scrollPosition),
      )
      designSurface.zoomController.zoom(ZoomType.OUT)

      assertEquals(designSurface.zoomController.scale, origScale)
      designSurface.zoomController.zoom(ZoomType.OUT)
      assertEquals(designSurface.zoomController.scale, origScale)

      designSurface.setScrollViewSizeAndValidateForTest(2000, 2000)
      assertEquals(1.0, designSurface.zoomController.minScale)

      designSurface.zoomController.setScale(1.099, 0, 0)
      scale = designSurface.zoomController.scale
      designSurface.zoomController.zoom(ZoomType.IN)
      assertTrue(designSurface.zoomController.scale > scale)
    }

  fun ignore_testZoomHiDPIScreen() {
    val model =
      model(
          "my_linear.xml",
          component(SdkConstants.LINEAR_LAYOUT)
            .withBounds(0, 0, 200, 200)
            .matchParentWidth()
            .matchParentHeight()
            .children(
              component(SdkConstants.FRAME_LAYOUT)
                .withBounds(100, 100, 100, 100)
                .width("100dp")
                .height("100dp")
            ),
        )
        .build()
    val config = model.configuration.clone()
    config.getFullConfig().setDensityQualifier(DensityQualifier(Density.XHIGH))
    model.configuration = config
    designSurface.setModel(model)
    assertEquals(2f, designSurface.getSceneManager(model)!!.sceneScalingFactor)
    designSurface.setScrollViewSizeAndValidateForTest(1000, 1000)
    designSurface.zoomController.zoomToFit()
    val origScale = designSurface.zoomController.scale
    assertEquals(origScale, designSurface.zoomController.minScale)

    val view = designSurface.focusedSceneView
    assertEquals(
      Point(-122, -122),
      Coordinates.getAndroidCoordinate(view!!, designSurface.pannable.scrollPosition),
    )

    designSurface.zoomController.zoom(ZoomType.IN)
    var scale = designSurface.zoomController.scale
    assertTrue(scale > origScale)
    assertEquals(
      Point(-44, -44),
      Coordinates.getAndroidCoordinate(view, designSurface.pannable.scrollPosition),
    )

    designSurface.zoomController.zoom(ZoomType.IN, 100, 100)
    assertTrue(designSurface.zoomController.scale > scale)
    assertEquals(
      Point(-29, -29),
      Coordinates.getAndroidCoordinate(view, designSurface.pannable.scrollPosition),
    )

    designSurface.zoomController.zoom(ZoomType.OUT, 100, 100)
    assertEquals(
      Point(-43, -43),
      Coordinates.getAndroidCoordinate(view, designSurface.pannable.scrollPosition),
    )
    designSurface.zoomController.zoom(ZoomType.OUT)
    assertEquals(
      Point(-122, -122),
      Coordinates.getAndroidCoordinate(view, designSurface.pannable.scrollPosition),
    )
    designSurface.zoomController.zoom(ZoomType.OUT)

    assertEquals(designSurface.zoomController.scale, origScale)
    designSurface.zoomController.zoom(ZoomType.OUT)
    assertEquals(designSurface.zoomController.scale, origScale)

    designSurface.setScrollViewSizeAndValidateForTest(2000, 2000)
    assertEquals(1.0, designSurface.zoomController.minScale)

    designSurface.zoomController.setScale(1.099, 0, 0)
    scale = designSurface.zoomController.scale
    designSurface.zoomController.zoom(ZoomType.IN)
    assertTrue(designSurface.zoomController.scale > scale)
  }

  fun ignore_testCanZoomToFit() {
    val model: NlModel =
      model(
          "absolute.xml",
          component(SdkConstants.ABSOLUTE_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight(),
        )
        .build()
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on
    // the Material theme
    model.configuration.setTheme("android:Theme.NoTitleBar.Fullscreen")
    designSurface.setModel(model)
    designSurface.setSize(1000, 1000)
    designSurface.doLayout()

    designSurface.zoomController.zoom(ZoomType.IN)
    assertTrue(designSurface.zoomController.canZoomOut())
    assertTrue(designSurface.zoomController.canZoomIn())
    designSurface.zoomController.setScale(designSurface.zoomController.minScale, -1, -1)
    assertTrue(designSurface.zoomController.canZoomIn())
    assertFalse(designSurface.zoomController.canZoomOut())
    designSurface.zoomController.zoomToFit()
    assertFalse(designSurface.zoomController.canZoomToFit())
    assertTrue(designSurface.zoomController.canZoomIn())
    assertFalse(designSurface.zoomController.canZoomOut())
  }

  fun ignore_testCannotZoomToFit() {
    val model: NlModel =
      model(
          "absolute.xml",
          component(SdkConstants.ABSOLUTE_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight(),
        )
        .build()

    val surfaceWidth = 500
    val surfaceHeight = 500

    // First use an empty surface to measure the zoom-to-fit scale.
    var surface = builder(project, getTestRootDisposable()).build()
    // TODO(b/370994254): it may be necessary to render after adding the model here
    surface.addModelsWithoutRender(listOf(model))
    surface.setSize(surfaceWidth, surfaceHeight)
    surface.doLayout()
    surface.zoomController.zoomToFit()
    val fitScale = surface.zoomController.scale
    surface.removeModels(listOf(model))

    // Create another surface which the minimum scale is larger than fitScale.
    surface = builder(project, getTestRootDisposable()).build()
    // TODO(b/370994254): it may be necessary to render after adding the model here
    surface.addModelsWithoutRender(listOf(model))
    surface.setSize(surfaceWidth, surfaceHeight)
    surface.doLayout()
    // Cannot zoom lower than min scale.
    surface.zoomController.zoomToFit()
    assertEquals(fitScale * 2, surface.zoomController.scale, 0.01)
    assertFalse(surface.zoomController.canZoomToFit())
    surface.removeModels(listOf(model))

    // Create another surface which the maximum scale is lower than fitScale.
    surface = builder(project, getTestRootDisposable()).build()
    // TODO(b/370994254): it may be necessary to render after adding the model here
    surface.addModelsWithoutRender(listOf(model))
    surface.setSize(surfaceWidth, surfaceHeight)
    surface.doLayout()
    // Cannot zoom larger than max scale.
    surface.zoomController.zoomToFit()
    assertEquals(fitScale / 2, surface.zoomController.scale, 0.01)
    assertFalse(surface.zoomController.canZoomToFit())
    surface.removeModels(listOf(model))
  }

  /** Test that we don't have any negative scale in case the windows size becomes too small */
  fun ignore_testsMinScale() {
    val model: NlModel =
      model(
          "absolute.xml",
          component(SdkConstants.ABSOLUTE_LAYOUT)
            .withBounds(0, 0, 1000, 1000)
            .matchParentWidth()
            .matchParentHeight(),
        )
        .build()
    val surface = designSurface
    surface.setModel(model)
    surface.setBounds(0, 0, 1000, 1000)
    surface.validate()
    surface.getLayout().layoutContainer(surface)
    surface.validateScrollArea()
    surface.zoomController.zoomToFit()
    assertEquals(0.5, surface.zoomController.scale, 0.1)

    surface.setBounds(0, 0, 1, 1)
    surface.revalidateScrollArea()
    surface.validate()
    surface.layout.layoutContainer(surface)
    surface.zoomController.zoomToFit()
    assertEquals(0.01, surface.zoomController.scale)
  }

  fun testNlSupportedActions() {
    val surface = designSurface
    // All NlSupportedActions are supported by default in the NlDesignSurface
    for (value in NlSupportedActions.entries) {
      assertTrue(surface.isActionSupported(value))
    }
  }

  private fun refreshSurface() {
    for (manager in designSurface.sceneManagers) {
      // TODO (b/370994254): it may be necessary to make this method suspendable and replace this
      //  with requestRenderAndWait()
      manager.requestRender()
    }
  }

  private fun assertComponentWithId(model: NlModel, expectedId: String) {
    val component = model.treeReader.find(expectedId)
    assertNotNull(
      "Expected id is \"" +
        expectedId +
        "\" but current ids are: " +
        model.treeReader
          .flattenComponents()
          .map<String?> { obj: NlComponent? -> obj!!.getId() }
          .collect(Collectors.joining(", ")),
      component,
    )
  }
}

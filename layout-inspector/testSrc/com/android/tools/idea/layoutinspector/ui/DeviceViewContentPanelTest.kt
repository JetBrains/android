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
package com.android.tools.idea.layoutinspector.ui

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.ScreenHeightQualifier
import com.android.ide.common.resources.configuration.ScreenRoundQualifier
import com.android.ide.common.resources.configuration.ScreenWidthQualifier
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.resources.ScreenRound
import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils.resolveWorkspacePathUnchecked
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.android.tools.adtui.swing.FakeMouse
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.ide.ui.SelectProcessAction
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.common.SelectViewAction
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.COMPOSE1
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ROOT
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.VIEW2
import com.android.tools.idea.layoutinspector.model.VIEW3
import com.android.tools.idea.layoutinspector.model.VIEW4
import com.android.tools.idea.layoutinspector.model.WINDOW_MANAGER_FLAG_DIM_BEHIND
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.appinspection.Screenshot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewAndroidWindow
import com.android.tools.idea.layoutinspector.tree.GotoDeclarationAction
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.DropDownActionWithButton
import com.android.tools.idea.layoutinspector.ui.toolbar.actions.RECOMPOSITION_COLOR_ORANGE_ARGB
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.view
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.runDispatching
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.android.tools.layoutinspector.BitmapType
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Cursor
import java.awt.Cursor.HAND_CURSOR
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Polygon
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.nio.file.Path
import java.util.function.Supplier
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JPopupMenu
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"
private const val DIFF_THRESHOLD = 0.01
private const val DIFF_THRESHOLD_TEXT = 0.5
private val activityMain =
  ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")

class DeviceViewContentPanelTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val fileOpenCaptureRule = FileOpenCaptureRule(projectRule)

  @get:Rule val disposable = DisposableRule()

  @get:Rule
  val chain =
    RuleChain.outerRule(projectRule)
      .around(fileOpenCaptureRule)
      .around(EdtRule())
      .around(IconLoaderRule())!!

  @get:Rule val fontRule = PortableUiFontRule()

  private var testDataPath: Path = Path.of("")

  @Before
  fun before() {
    ApplicationManager.getApplication()
      .replaceService(
        PropertiesComponent::class.java,
        PropertiesComponentMock(),
        disposable.disposable,
      )
    testDataPath = resolveWorkspacePathUnchecked(TEST_DATA_PATH)
  }

  @Test
  fun testSize() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 100, 200) {
          view(VIEW1, 0, 0, 50, 50) { view(VIEW3, 30, 30, 10, 10) }
          view(VIEW2, 60, 160, 10, 20)
        }
      }
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false
    val renderSettings = FakeRenderSettings()
    renderSettings.scalePercent = 30
    renderSettings.drawLabel = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    assertEquals(Dimension(130, 160), panel.preferredSize)
    panel.renderModel.rotate(1.0, 0.0)
    assertEquals(Dimension(376, 395), panel.preferredSize)

    renderSettings.scalePercent = 100
    assertEquals(Dimension(1020, 1084), panel.preferredSize)

    model.update(window(ROOT, ROOT, 0, 0, 100, 200) { view(VIEW1, 0, 0, 50, 50) }, listOf(ROOT), 0)
    // This is usually handled by a listener registered in DeviceViewPanel
    panel.renderModel.refresh()
    assertEquals(Dimension(200, 300), panel.preferredSize)
    panel.renderModel.rotate(1.0, 0.0)
    assertEquals(Dimension(732, 820), panel.preferredSize)
  }

  @Test
  fun testPaint() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 100, 100, 150) {
          view(VIEW1, 10, 115, 25, 25) { image() }
          compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 150, width = 80, height = 50)
        }
      }
    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(120, 200, TYPE_INT_ARGB)
    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(120, 200)

    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    renderSettings.scalePercent = 50
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_scaled.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.layerSpacing = 3
    renderSettings.scalePercent = 100
    panel.renderModel.rotate(0.3, 0.2)
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_rotated.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.layerSpacing = 1
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_spacing1.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.layerSpacing = 15
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_spacing2.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.layerSpacing = 3
    val windowRoot = model[ROOT]!!
    model.setSelection(windowRoot, SelectionOrigin.INTERNAL)
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_selected.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    renderSettings.drawLabel = true
    model.setSelection(model[COMPOSE1], SelectionOrigin.INTERNAL)
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_label.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    treeSettings.showRecompositions = true
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_count.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    val compose1 = model[COMPOSE1] as ComposeViewNode
    compose1.qualifiedName = "LongName" // hides the recomposition count
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_label_hides_count.png"),
      generatedImage,
      0.2,
    )
    compose1.qualifiedName = "Text"

    renderSettings.drawBorders = false
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_noborders.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    model.hoveredNode = windowRoot
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_hovered.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    model.setSelection(null, SelectionOrigin.INTERNAL)
    renderSettings.drawBorders = true
    model.hoveredNode = null
    model.maxHighlight = 17f
    compose1.recompositions.highlightCount = 17f

    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_highlight1.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    compose1.recompositions.highlightCount = 7f
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_highlight2.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    compose1.recompositions.highlightCount = 17f
    renderSettings.recompositionColor = RECOMPOSITION_COLOR_ORANGE_ARGB
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_highlight3.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    compose1.recompositions.highlightCount = 7f
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_highlight4.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    renderSettings.drawBorders = false
    paint(panel, generatedImage)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_highlight5.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )
  }

  @Test
  fun testPaintWithFold() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 20, 40) {
          view(VIEW1, 3, 3, 14, 14) {
            view(VIEW2, 6, 6, 8, 8)
            image()
          }
        }
      }
    model.foldInfo =
      InspectorModel.FoldInfo(
        97,
        InspectorModel.Posture.HALF_OPEN,
        InspectorModel.FoldOrientation.VERTICAL,
      )
    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(130, 250, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val renderSettings = EditorRenderSettings(scalePercent = 100)
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false
    val client = mock<InspectorClient>()
    whenever(client.capabilities).thenReturn(setOf(InspectorClient.Capability.SUPPORTS_SKP))
    whenever(client.stats).thenAnswer { mock<SessionStatistics>() }

    val panel =
      createDeviceViewContentPanel(
        disposable.disposable,
        model,
        treeSettings,
        renderSettings,
        { client },
      )

    panel.setSize(130, 250)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintFold.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    panel.renderModel.layerSpacing = 10
    panel.renderModel.rotate(0.5, 0.7)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintFold_rotated.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    panel.renderModel.hoveredDrawInfo = panel.renderModel.hitRects.find { it.node is DrawViewImage }
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintFold_hovered.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintFold_hovered_selected.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    panel.renderModel.hoveredDrawInfo = null
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintFold_selected.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )

    renderSettings.drawFold = false
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      testDataPath.resolve("testPaintFold_no_fold.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
    renderSettings.drawFold = true

    panel.setSize(350, 100)
    @Suppress("UndesirableClassUsage") val generatedImage2 = BufferedImage(350, 100, TYPE_INT_ARGB)
    model.foldInfo =
      InspectorModel.FoldInfo(
        97,
        InspectorModel.Posture.HALF_OPEN,
        InspectorModel.FoldOrientation.HORIZONTAL,
      )
    graphics = generatedImage2.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintFold_horizontal.png"),
      generatedImage2,
      DIFF_THRESHOLD_TEXT,
    )
  }

  @Test
  fun testPaintWithHiddenSystemViews() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 20, 40, layout = null) {
          view(VIEW1, 5, 6, 10, 10, layout = activityMain) { image() }
        }
      }
    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(40, 60, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(40, 60)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithHiddenSystemViews.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    renderSettings.scalePercent = 100
    panel.renderModel.layerSpacing = 3
    panel.renderModel.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithHiddenSystemView_rotated.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.layerSpacing = 10
    val windowRoot = model[ROOT]!!
    model.setSelection(windowRoot, SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithHiddenSystemView_selected.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Test
  fun testRotationDoesntThrow() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 500, 1000) {
          // Use an RTL name to force TextLayout to be used
          view(VIEW1, 125, 150, 250, 250, qualifiedName = "שמי העברי") { image() }
        }
      }

    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(10, 15, TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()

    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val renderSettings = InspectorRenderSettings()
    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(10, 15)
    panel.renderModel.rotate(-1.0, -1.0)

    for (i in 0..20) {
      panel.renderModel.rotate(-2.0, 0.1)
      for (j in 0..20) {
        panel.renderModel.rotate(0.1, 0.0)
        panel.paint(graphics)
      }
    }
  }

  @Test
  fun testOverlay() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 600, 600) { view(VIEW1, 125, 150, 250, 250) }
      }

    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(1000, 1500, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(1000, 1500)

    panel.renderModel.overlay =
      ImageIO.read(resolveWorkspacePathUnchecked("$TEST_DATA_PATH/overlay.png").toFile())

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_overlay-60.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.overlayAlpha = 0.2f
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_overlay-20.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.overlayAlpha = 0.9f
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaint_overlay-90.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Test
  fun testDrag() {
    val model =
      model(disposable.disposable) { view(ROOT, 0, 0, 100, 200) { view(VIEW1, 25, 30, 50, 50) } }

    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false
    val client: InspectorClient = mock()
    whenever(client.stats).thenAnswer { mock<SessionStatistics>() }

    val panel =
      createDeviceViewContentPanel(
        disposable.disposable,
        model,
        treeSettings,
        renderSettings,
        { client },
      )

    whenever(client.capabilities).thenReturn(setOf(InspectorClient.Capability.SUPPORTS_SKP))
    val layoutInspector: LayoutInspector = mock()
    whenever(layoutInspector.currentClient).thenReturn(client)
    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider { id ->
      when (id) {
        LAYOUT_INSPECTOR_DATA_KEY.name -> layoutInspector
        TOGGLE_3D_ACTION_BUTTON_KEY.name -> mock<ActionButton>()
        else -> null
      }
    }
    panel.setSize(200, 300)
    val fakeUi = FakeUi(panel)

    fakeUi.mouse.drag(10, 10, 50, 10)
    // We're not in rotated mode, so nothing should have happened yet.
    assertEquals(0.0, panel.renderModel.xOff)
    assertEquals(0.0, panel.renderModel.yOff)

    // Now modify the model to be rotated and verify that dragging changes the rotation
    panel.renderModel.xOff = 0.1
    fakeUi.mouse.drag(10, 10, 10, 10)
    assertEquals(0.11, panel.renderModel.xOff)
    assertEquals(0.01, panel.renderModel.yOff)

    // Simulate a connection change. This should reset the rotation
    model.connectionListeners.forEach { it.onConnectionChanged(client) }
    assertEquals(0.0, panel.renderModel.xOff)
    assertEquals(0.0, panel.renderModel.yOff)
  }

  @Test
  fun testClick() {
    val layoutMain =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")
    val layoutAppcompat =
      ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 100, 200) {
          view(VIEW1, 25, 30, 50, 50, layout = layoutMain) {
            view(VIEW2, 30, 35, 40, 40, layout = layoutAppcompat) {
              view(VIEW3, 35, 40, 30, 30, layout = layoutAppcompat)
            }
          }
        }
      }
    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(100, 200)
    val fakeUi = FakeUi(panel)
    assertThat(model.selection).isNull()

    // Click on VIEW3 when system views are showing:
    treeSettings.hideSystemNodes = false
    fakeUi.mouse.click(40, 50)
    assertThat(model.selection).isSameAs(model[VIEW3]!!)

    // Click on VIEW3 when system views are hidden:
    treeSettings.hideSystemNodes = true
    fakeUi.mouse.click(40, 50)
    assertThat(model.selection).isSameAs(model[VIEW1]!!)
  }

  @RunsInEdt
  @Test
  fun testDoubleClick() {
    projectRule.fixture.addFileToProject(
      "res/layout/activity_main.xml",
      """
      <LinearLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@+id/action_bar_root"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical">
        <TextView
            android:id="@+id/text1"
            android:text="Hello"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"/>
      </LinearLayout>
    """
        .trimIndent(),
    )
    val layoutMain =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")
    val layoutAppcompat =
      ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val model =
      model(projectRule.testRootDisposable, projectRule.project) {
        view(ROOT, 0, 0, 100, 200) {
          view(
            VIEW1,
            25,
            30,
            50,
            50,
            layout = layoutMain,
            viewId = ResourceReference(ResourceNamespace.ANDROID, ResourceType.ID, "text1"),
          ) {
            view(VIEW2, 30, 35, 40, 40, layout = layoutAppcompat) {
              view(VIEW3, 35, 40, 30, 30, layout = layoutAppcompat)
            }
          }
        }
      }
    model.resourceLookup.screenDimension = Dimension(100, 200)

    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(100, 200)
    val fakeUi = FakeUi(panel)
    assertThat(model.selection).isNull()

    treeSettings.hideSystemNodes = false

    fakeUi.mouse.click(27, 35)
    fakeUi.render()
    fakeUi.layoutAndDispatchEvents()
    assertThat(model.selection).isSameAs(model[VIEW1]!!)

    fakeUi.mouse.doubleClick(27, 35)
    runDispatching { GotoDeclarationAction.lastAction?.join() }
    fileOpenCaptureRule.checkEditor("activity_main.xml", 7, "<TextView")
  }

  @Test
  fun testMouseMove() {
    val layoutMain =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")
    val layoutAppcompat =
      ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 100, 200) {
          view(VIEW1, 25, 30, 50, 50, layout = layoutMain) {
            view(VIEW2, 30, 35, 40, 40, layout = layoutAppcompat) {
              view(VIEW3, 35, 40, 30, 30, layout = layoutAppcompat)
            }
          }
        }
      }
    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(100, 200)
    val fakeUi = FakeUi(panel)
    assertThat(model.hoveredNode).isNull()

    // Move to VIEW3 when system views are showing:
    fakeUi.mouse.moveTo(40, 50)
    assertThat(model.hoveredNode).isSameAs(model[VIEW3]!!)

    // Move to VIEW3 when system views are hidden:
    treeSettings.hideSystemNodes = true
    fakeUi.mouse.moveTo(40, 50)
    assertThat(model.hoveredNode).isSameAs(model[VIEW1]!!)
  }

  @Test
  fun testContextMenu() {
    val layoutMain =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")
    val layoutAppcompat =
      ResourceReference(ResourceNamespace.APPCOMPAT, ResourceType.LAYOUT, "abc_screen_simple")
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 100, 200, layout = null) {
          view(VIEW1, 25, 30, 50, 50, layout = layoutMain) {
            view(VIEW2, 30, 35, 40, 40, layout = layoutAppcompat) {
              view(VIEW3, 35, 40, 30, 30, layout = layoutAppcompat) {
                view(VIEW4, 36, 41, 25, 25, layout = layoutMain)
              }
            }
          }
        }
      }
    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(100, 200)
    val fakeUi = FakeUi(panel)
    assertThat(model.selection).isNull()

    var latestPopup: FakeActionPopupMenu? = null
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, mock(), disposable.disposable)
    doAnswer { invocation ->
        latestPopup = FakeActionPopupMenu(invocation.getArgument(1))
        latestPopup
      }
      .whenever(ActionManager.getInstance())
      .createActionPopupMenu(anyString(), any(ActionGroup::class.java))

    // Right click on VIEW4 when system views are showing:
    fakeUi.mouse.click(40, 50, FakeMouse.Button.RIGHT)
    latestPopup!!.assertSelectViewActionAndGotoDeclaration(5, 4, 3, 2, 1)

    // Right click on VIEW4 when system views are hidden:
    treeSettings.hideSystemNodes = true
    fakeUi.mouse.click(40, 50, FakeMouse.Button.RIGHT)
    latestPopup!!.assertSelectViewActionAndGotoDeclaration(5, 2)
  }

  @Test
  fun testEmptyTextVisibility() {
    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val model = model(disposable.disposable) {}
    val launcher: InspectorClientLauncher = mock()
    val client = mock<InspectorClient>()
    whenever(client.stats).thenAnswer { mock<SessionStatistics>() }
    whenever(launcher.activeClient).thenReturn(client)
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false
    val selectProcessAction = mock<SelectProcessAction>()
    whenever(selectProcessAction.templatePresentation).thenReturn(mock())
    val dropDownActionWithButton = DropDownActionWithButton(selectProcessAction) { null }

    val panel =
      createDeviceViewContentPanel(
        disposable.disposable,
        model,
        treeSettings,
        renderSettings,
        { client },
        dropDownActionWithButton,
      )

    panel.setSize(200, 200)
    val fakeUi = FakeUi(panel)
    val hand = Cursor.getPredefinedCursor(HAND_CURSOR)
    mockStatic(BrowserUtil::class.java).use { browserUtil ->
      for (x in 0..200) {
        for (y in 0..200) {
          fakeUi.mouse.moveTo(x, y)
          if (panel.cursor == hand) {
            fakeUi.mouse.click(x, y)
          }
        }
      }
      browserUtil.verify(
        { BrowserUtil.browse("https://developer.android.com/studio/debug/layout-inspector") },
        atLeastOnce(),
      )
      verify(selectProcessAction, atLeastOnce()).actionPerformed(any())
    }

    model.update(window(ROOT, ROOT) { view(VIEW1) }, listOf(ROOT), 1)
    for (x in 0..200) {
      for (y in 0..200) {
        fakeUi.mouse.moveTo(x, y)
        assertThat(panel.cursor).isNotEqualTo(hand)
      }
    }
  }

  @Test
  fun testPaintMultiWindow() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 100, 200) { view(VIEW1, 0, 0, 50, 50) { image() } }
      }

    // Second window. Root doesn't overlap with top of first window--verify they're on separate
    // levels in the drawing.
    val window2 = window(VIEW2, VIEW2, 60, 60, 30, 30) { view(VIEW3, 70, 70, 10, 10) }

    model.update(window2, listOf(ROOT, VIEW2), 0)

    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(200, 300)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintMultiWindow.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[VIEW3], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintMultiWindow_selected.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintMultiWindow_rotated.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Test
  fun testPaintMultiWindowDimBehind() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 100, 200) { view(VIEW1, 0, 0, 50, 50) { image() } }
      }

    // Second window. Root doesn't overlap with top of first window--verify they're on separate
    // levels in the drawing.
    val window2 =
      window(VIEW2, VIEW2, 60, 60, 30, 30, layoutFlags = WINDOW_MANAGER_FLAG_DIM_BEHIND) {
        view(VIEW3, 70, 70, 10, 10)
      }

    model.update(window2, listOf(ROOT, VIEW2), 0)

    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(200, 300, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(200, 300)
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintMultiWindowDimBehind.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.rotate(0.3, 0.2)
    renderSettings.scalePercent = 50
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintMultiWindowDimBehind_rotated.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Test
  fun testPaintWithImages() {
    val image1 = ImageIO.read(resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image1.png").toFile())
    val image2 = ImageIO.read(resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image2.png").toFile())
    val image3 = ImageIO.read(resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image3.png").toFile())

    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 585, 804) {
          image(image1)
          view(VIEW1, 0, 100, 585, 585) { image(image2) }
          view(VIEW2, 100, 400, 293, 402) { image(image3) }
        }
      }

    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(350, 450, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val renderSettings = FakeRenderSettings()
    renderSettings.scalePercent = 50
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(350, 450)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImages.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[ROOT], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImages_root.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImages_view1.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[VIEW2], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImages_view2.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImages_view2.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImages_view2.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    renderSettings.drawLabel = true
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImages_label.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Suppress("UndesirableClassUsage")
  @Test
  fun testPaintWithImagesBetweenChildren() {
    val image1 = BufferedImage(40, 40, TYPE_INT_ARGB)
    image1.graphics.run {
      color = Color.RED
      fillRect(10, 10, 20, 20)
    }
    val image2 = BufferedImage(40, 40, TYPE_INT_ARGB)
    image2.graphics.run {
      color = Color.BLUE
      fillRect(0, 0, 24, 24)
    }
    val image3 = BufferedImage(20, 20, TYPE_INT_ARGB)
    image3.graphics.run {
      color = Color.GREEN
      fillRect(0, 0, 20, 20)
    }

    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 40, 40) {
          view(VIEW1, 0, 0, 40, 40) { image(image2) }
          image(image1)
          view(VIEW2, 20, 20, 20, 20) { image(image3) }
        }
      }

    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(120, 140, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val renderSettings = InspectorRenderSettings()
    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(120, 140)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImagesBetweenChildren.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.layerSpacing = 60
    panel.renderModel.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked(
        "$TEST_DATA_PATH/testPaintWithImagesBetweenChildren_rotated.png"
      ),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[ROOT], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithImagesBetweenChildren_root.png"),
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )
  }

  @Test
  fun testPaintWithRootImageOnly() {
    val image1 = ImageIO.read(resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image1.png").toFile())

    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 585, 804) {
          image(image1)
          view(VIEW1, 0, 100, 585, 585)
          view(VIEW2, 100, 400, 293, 402)
        }
      }

    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(350, 450, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val renderSettings = FakeRenderSettings()
    renderSettings.scalePercent = 50
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(350, 450)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithRootImageOnly.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[ROOT], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithRootImageOnly_root.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithRootImageOnly_view1.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Test
  @Suppress("UndesirableClassUsage")
  fun testPaintTransformed() {
    val image1 = BufferedImage(220, 220, TYPE_INT_ARGB)
    (image1.graphics as Graphics2D).run {
      paint = GradientPaint(0f, 40f, Color.RED, 220f, 180f, Color.BLUE)
      fill(Polygon(intArrayOf(0, 180, 220, 40), intArrayOf(40, 0, 180, 220), 4))
    }

    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 400, 600) {
          view(
            VIEW1,
            50,
            100,
            300,
            300,
            bounds = Polygon(intArrayOf(90, 270, 310, 130), intArrayOf(180, 140, 320, 360), 4),
          ) {
            image(image1)
          }
        }
      }

    val generatedImage = BufferedImage(400, 600, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()

    val renderSettings = FakeRenderSettings()
    renderSettings.scalePercent = 50
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(400, 600)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintTransformed.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintTransformed_view1.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    renderSettings.drawLabel = true
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintTransformed_label.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    renderSettings.drawUntransformedBounds = true
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintTransformed_untransformed.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    renderSettings.drawBorders = false
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintTransformed_onlyUntransformed.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Test
  @Suppress("UndesirableClassUsage")
  fun testPaintTransformedOutsideRoot() {
    val image1 = BufferedImage(80, 150, TYPE_INT_ARGB)
    (image1.graphics as Graphics2D).run {
      paint = GradientPaint(0f, 0f, Color.RED, 80f, 100f, Color.BLUE)
      fillRect(0, 0, 80, 100)
    }

    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 100, 100) {
          view(
            VIEW1,
            20,
            20,
            60,
            60,
            bounds = Polygon(intArrayOf(-20, 80, 80, -20), intArrayOf(-50, -50, 150, 150), 4),
          ) {
            image(image1)
          }
        }
      }

    val generatedImage = BufferedImage(200, 200, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()

    val renderSettings = FakeRenderSettings()
    renderSettings.scalePercent = 75
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(200, 200)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintTransformedOutsideRoot.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    model.setSelection(model[VIEW1], SelectionOrigin.INTERNAL)
    graphics = generatedImage.createGraphics()
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintTransformedOutsideRoot_view1.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @RunsInEdt
  @Test
  fun testAutoScroll() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 300, 400) {
          view(VIEW1, 0, 0, 50, 50) { view(VIEW3, 30, 30, 10, 10) }
          view(VIEW2, 60, 160, 10, 20)
        }
      }
    val view1 = model[VIEW1]
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val renderSettings = InspectorRenderSettings()
    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    val scrollPane = JBScrollPane(panel)
    panel.setBounds(0, 0, 1000, 1000)
    scrollPane.setBounds(0, 0, 200, 200)
    scrollPane.viewport.viewPosition = Point(100, 100)
    UIUtil.dispatchAllInvocationEvents()

    // No auto scrolling when selection is changed from the image:
    model.setSelection(view1, SelectionOrigin.INTERNAL)
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(100, 100))

    // Auto scrolling will be in effect when selecting from the component tree:
    model.setSelection(view1, SelectionOrigin.COMPONENT_TREE)
    assertThat(scrollPane.viewport.viewPosition).isEqualTo(Point(348, 277))
  }

  private class FakeActionPopupMenu(private val group: ActionGroup) : ActionPopupMenu {
    val popup: JPopupMenu = mock()

    override fun getComponent(): JPopupMenu = popup

    override fun getActionGroup(): ActionGroup = group

    override fun getPlace(): String = error("Not implemented")

    override fun setTargetComponent(component: JComponent) = error("Not implemented")

    override fun setDataContext(dataProvider: Supplier<out DataContext>) = error("Not implemented")

    fun assertSelectViewActionAndGotoDeclaration(vararg expected: Long) {
      val event: AnActionEvent = mock()
      whenever(event.actionManager).thenReturn(ActionManager.getInstance())
      val actions = group.getChildren(event)
      assertThat(actions.size).isEqualTo(2)
      assertThat(actions[0]).isInstanceOf(DropDownAction::class.java)
      val selectActions = (actions[0] as DropDownAction).getChildren(event)
      assertThat(
          selectActions.toList().filterIsInstance(SelectViewAction::class.java).map {
            it.view.drawId
          }
        )
        .containsExactlyElementsIn(expected.toList())
      assertThat(actions[1]).isEqualTo(GotoDeclarationAction)
    }
  }

  @Suppress("UndesirableClassUsage")
  @Test
  fun testPaintRound() {
    val screenshot = Screenshot("wear.png", BitmapType.RGB_565)

    val viewLayoutEvent =
      LayoutInspectorViewProtocol.LayoutEvent.newBuilder()
        .apply {
          screenshotBuilder.apply {
            type = LayoutInspectorViewProtocol.Screenshot.Type.BITMAP
            bytes = ByteString.copyFrom(screenshot.bytes)
          }
        }
        .build()

    val model = model(disposable.disposable) {}
    val folderConfiguration =
      FolderConfiguration().apply {
        screenRoundQualifier = ScreenRoundQualifier(ScreenRound.ROUND)
        screenWidthQualifier = ScreenWidthQualifier.getQualifier("454")
        screenHeightQualifier = ScreenHeightQualifier.getQualifier("454")
        densityQualifier = DensityQualifier(Density.MEDIUM)
      }

    val window =
      ViewAndroidWindow(
        mock(),
        mock(),
        view(1, 0, 0, 454, 454) {
          view(2, 10, 10, 100, 100)
          view(3, 300, 400, 50, 50)
        },
        viewLayoutEvent,
        folderConfiguration,
        { false },
        {},
      )
    model.update(window, listOf(1L), 2)
    runBlocking { model.windows[1L]?.refreshImages(1.0) }

    val generatedImage = BufferedImage(350, 450, TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()

    val renderSettings = FakeRenderSettings()
    renderSettings.scalePercent = 50
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(350, 450)

    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/wear_round_expected.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Test
  fun testPaintWithChildrenOutsideParent() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 20, 40) {
          view(VIEW1, 0, 0, 20, 40)
          image()
          view(VIEW2, -23, 0, 20, 40)
          view(VIEW3, 0, 0, 20, 40)
        }
      }
    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(90, 70, TYPE_INT_ARGB)
    var graphics = generatedImage.createGraphics()

    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(90, 70)
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithChildrenOutsideParent.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )

    panel.renderModel.layerSpacing = 30
    renderSettings.scalePercent = 100
    panel.renderModel.rotate(0.3, 0.2)
    graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked(
        "$TEST_DATA_PATH/testPaintWithChildrenOutsideParent_rotated.png"
      ),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  @Test
  fun testPaintWithChildAboveSibling() {
    val model =
      model(disposable.disposable) {
        view(ROOT, 0, 0, 20, 40) {
          view(VIEW1, 0, 0, 20, 20) { view(VIEW2, 0, 0, 20, 40) }
          view(VIEW3, 0, 20, 20, 20)
        }
      }

    val renderSettings = FakeRenderSettings()
    renderSettings.drawLabel = false
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false

    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(70, 70)

    panel.renderModel.layerSpacing = 30
    renderSettings.scalePercent = 100
    panel.renderModel.rotate(0.3, 0.2)

    @Suppress("UndesirableClassUsage") val generatedImage = BufferedImage(70, 70, TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilar(
      resolveWorkspacePathUnchecked("$TEST_DATA_PATH/testPaintWithChildAboveSibling.png"),
      generatedImage,
      DIFF_THRESHOLD,
    )
  }

  private fun paint(panel: DeviceViewContentPanel, on: BufferedImage) {
    val graphics = on.createGraphics()
    // Set the font such that it will be the same across platforms
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    panel.paint(graphics)
  }
}

class DeviceViewContentPanelWithScaledFontTest {
  @get:Rule val projectRule = ProjectRule()

  @get:Rule val fontRule = PortableUiFontRule(2.0f)

  @get:Rule val disposable = DisposableRule()

  @Test
  fun testPaintEmpty() {
    ApplicationManager.getApplication()
      .replaceService(ActionManager::class.java, mock(), disposable.disposable)
    val treeSettings = FakeTreeSettings(showRecompositions = false)
    treeSettings.hideSystemNodes = false
    val model = model(disposable.disposable) {}

    val renderSettings = FakeRenderSettings()
    val panel =
      createDeviceViewContentPanel(disposable.disposable, model, treeSettings, renderSettings)

    panel.setSize(800, 400)
    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(panel.width, panel.height, TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()
    panel.paint(graphics)
    ImageDiffUtil.assertImageSimilarPerPlatform(
      resolveWorkspacePathUnchecked(TEST_DATA_PATH),
      "testPaintEmpty",
      generatedImage,
      DIFF_THRESHOLD_TEXT,
    )
  }
}

private fun createDeviceViewContentPanel(
  disposable: Disposable,
  model: InspectorModel,
  treeSettings: TreeSettings,
  renderSettings: RenderSettings,
  clientProvider: () -> InspectorClient = { DisconnectedClient },
  selectTargetAction: DropDownActionWithButton? = mock(),
  renderModel: RenderModel = RenderModel(model, mock(), treeSettings, clientProvider),
  renderLogic: RenderLogic = RenderLogic(renderModel, renderSettings),
): DeviceViewContentPanel {
  return DeviceViewContentPanel(
    inspectorModel = model,
    deviceModel = null,
    treeSettings = treeSettings,
    currentClient = clientProvider,
    pannable = mock(),
    selectTargetAction = selectTargetAction,
    disposableParent = disposable,
    isLoading = { false },
    isCurrentForegroundProcessDebuggable = { false },
    hasForegroundProcess = { false },
    renderModel = renderModel,
    renderLogic = renderLogic,
    coroutineScope = AndroidCoroutineScope(disposable),
  )
}

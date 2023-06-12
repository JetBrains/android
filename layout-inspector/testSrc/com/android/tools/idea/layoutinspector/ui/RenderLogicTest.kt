/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
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
import com.android.tools.idea.layoutinspector.model.WINDOW_MANAGER_FLAG_DIM_BEHIND
import com.android.tools.idea.layoutinspector.pipeline.appinspection.Screenshot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewAndroidWindow
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.view
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.window
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.layoutinspector.BitmapType
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.jetbrains.rd.swing.fillRect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import java.awt.Color
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.pathString

private val TEST_DATA_PATH = Path.of("tools", "adt", "idea", "layout-inspector", "testData")
private const val DIFF_THRESHOLD = 0.2
private val activityMain = ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT, "activity_main")

class RenderLogicTest {
  private lateinit var treeSettings: TreeSettings
  private lateinit var renderSettings: RenderSettings

  private data class TestConfig(
    val inspectorModel: InspectorModel,
    val renderSettings: RenderSettings,
    val renderModel: RenderModel,
    val renderLogic: RenderLogic,
    val renderImage: BufferedImage,
    val renderDimension: Dimension,
    val centerTransform: AffineTransform
    )

  @Before
  fun setUp() {
    treeSettings = FakeTreeSettings()
    renderSettings = FakeRenderSettings()
  }

  @get:Rule
  val testName = TestName()

  val projectRule = ProjectRule()

  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(EdtRule()).around(IconLoaderRule())!!

  @get:Rule
  val fontRule = PortableUiFontRule()

  @Test
  fun testPaintBorders() {
    val (_, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersScaled() {
    val (_, renderSettings, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    renderSettings.scalePercent = 50
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersRotated() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    renderModel.layerSpacing = 3
    renderModel.rotate(0.3, 0.6)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersSpacingLow() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    renderModel.layerSpacing = 1
    renderModel.rotate(0.3, 0.6)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersSpacingHigh() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    renderModel.layerSpacing = 15
    renderModel.rotate(0.3, 0.6)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersSelectedNoLabel() {
    val (inspectorModel, renderSettings, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    val rootNode = inspectorModel[ROOT]!!
    renderSettings.drawLabel = false
    renderModel.layerSpacing = 3
    inspectorModel.setSelection(rootNode, SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersLabel() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    inspectorModel.setSelection(inspectorModel[COMPOSE1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersRecompositionCount() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    treeSettings.showRecompositions = true
    inspectorModel.setSelection(inspectorModel[COMPOSE1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersLabelHidesRecompositionCount() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    val composeNode = inspectorModel[COMPOSE1] as ComposeViewNode
    composeNode.qualifiedName = "LongName" // hides the recomposition count
    treeSettings.showRecompositions = true
    inspectorModel.setSelection(inspectorModel[COMPOSE1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersRecompositionCountNoBorders() {
    val (inspectorModel, renderSettings, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    treeSettings.showRecompositions = true
    renderSettings.drawBorders = false
    inspectorModel.setSelection(inspectorModel[COMPOSE1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersHovered() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    val rootNode = inspectorModel[ROOT]!!
    inspectorModel.hoveredNode = rootNode
    inspectorModel.setSelection(inspectorModel[COMPOSE1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersRecompositionHighlight() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    val composeNode = inspectorModel[COMPOSE1] as ComposeViewNode
    treeSettings.showRecompositions = true
    inspectorModel.maxHighlight = 17f
    composeNode.recompositions.highlightCount = 17f
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersRecompositionHighlightLowCount() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    val composeNode = inspectorModel[COMPOSE1] as ComposeViewNode
    treeSettings.showRecompositions = true
    inspectorModel.maxHighlight = 17f
    composeNode.recompositions.highlightCount = 7f
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersRecompositionHighlightOrange() {
    val (inspectorModel, renderSettings, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    val composeNode = inspectorModel[COMPOSE1] as ComposeViewNode
    treeSettings.showRecompositions = true
    inspectorModel.maxHighlight = 17f
    composeNode.recompositions.highlightCount = 17f
    renderSettings.highlightColor = HIGHLIGHT_COLOR_ORANGE
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersRecompositionHighlightOrangeLowCount() {
    val (inspectorModel, renderSettings, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    val composeNode = inspectorModel[COMPOSE1] as ComposeViewNode
    treeSettings.showRecompositions = true
    inspectorModel.maxHighlight = 17f
    composeNode.recompositions.highlightCount = 7f
    renderSettings.highlightColor = HIGHLIGHT_COLOR_ORANGE
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintBordersRecompositionHighlightNoBorder() {
    val (inspectorModel, renderSettings, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    val composeNode = inspectorModel[COMPOSE1] as ComposeViewNode
    treeSettings.showRecompositions = true
    renderSettings.drawBorders = false
    inspectorModel.maxHighlight = 17f
    composeNode.recompositions.highlightCount = 7f
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintImages() {
    val (_, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintImagesConfig()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintImagesRootSelected() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintImagesConfig()
    val rootView = inspectorModel[ROOT]!!
    inspectorModel.setSelection(rootView, SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintImagesView1Selected() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintImagesConfig()
    val view = inspectorModel[VIEW1]
    inspectorModel.setSelection(view, SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintImagesView2Selected() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintImagesConfig()
    val view = inspectorModel[VIEW2]
    inspectorModel.setSelection(view, SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintImagesView2SelectedLabelsOff() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintImagesConfig()
    val view = inspectorModel[VIEW2]
    renderSettings.drawLabel = false
    inspectorModel.setSelection(view, SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintFold() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintFoldConfig(130, 250)
    inspectorModel.foldInfo = InspectorModel.FoldInfo(97, InspectorModel.Posture.HALF_OPEN, InspectorModel.FoldOrientation.VERTICAL)
    renderSettings.drawFold = true
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintFoldRotated() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintFoldConfig(130, 250)
    inspectorModel.foldInfo = InspectorModel.FoldInfo(97, InspectorModel.Posture.HALF_OPEN, InspectorModel.FoldOrientation.VERTICAL)
    renderSettings.drawFold = true
    renderModel.layerSpacing = 10
    renderModel.rotate(0.5, 0.7)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintFoldRotatedHovered() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintFoldConfig(130, 250)
    inspectorModel.foldInfo = InspectorModel.FoldInfo(97, InspectorModel.Posture.HALF_OPEN, InspectorModel.FoldOrientation.VERTICAL)
    renderSettings.drawFold = true
    renderModel.layerSpacing = 10
    renderModel.rotate(0.5, 0.7)
    renderModel.hoveredDrawInfo = renderModel.hitRects.find { it.node is DrawViewImage }
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintFoldRotatedHoveredSelected() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintFoldConfig(130, 250)
    inspectorModel.foldInfo = InspectorModel.FoldInfo(97, InspectorModel.Posture.HALF_OPEN, InspectorModel.FoldOrientation.VERTICAL)
    renderSettings.drawFold = true
    renderModel.layerSpacing = 10
    renderModel.rotate(0.5, 0.7)
    renderModel.hoveredDrawInfo = renderModel.hitRects.find { it.node is DrawViewImage }
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintFoldRotatedSelected() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintFoldConfig(130, 250)
    inspectorModel.foldInfo = InspectorModel.FoldInfo(97, InspectorModel.Posture.HALF_OPEN, InspectorModel.FoldOrientation.VERTICAL)
    renderSettings.drawFold = true
    renderModel.layerSpacing = 10
    renderModel.rotate(0.5, 0.7)
    renderModel.hoveredDrawInfo = null
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintFoldNoFold() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintFoldConfig(130, 250)
    inspectorModel.foldInfo = InspectorModel.FoldInfo(97, InspectorModel.Posture.HALF_OPEN, InspectorModel.FoldOrientation.VERTICAL)
    renderSettings.drawFold = false
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintFoldHorizontal() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintFoldConfig(450, 100)
    inspectorModel.foldInfo = InspectorModel.FoldInfo(97, InspectorModel.Posture.HALF_OPEN, InspectorModel.FoldOrientation.HORIZONTAL)
    renderSettings.drawFold = true
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithHiddenSystemViews() {
    val (_, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithHiddenSystemViews()
    renderSettings.drawLabel = false
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithHiddenSystemViewsRotated() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithHiddenSystemViews()
    renderSettings.drawLabel = false
    renderModel.layerSpacing = 3
    renderModel.rotate(0.3, 0.2)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithHiddenSystemViewsSelected() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithHiddenSystemViews()
    renderSettings.drawLabel = false
    renderModel.rotate(0.3, 0.2)
    val windowRoot = inspectorModel[ROOT]!!
    inspectorModel.setSelection(windowRoot, SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintOverlay() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    renderModel.overlay = ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/overlay.png").toFile())
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintOverlayAlpha20() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    renderModel.overlay = ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/overlay.png").toFile())
    renderModel.overlayAlpha = 0.2f
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintOverlayAlpha90() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintBordersConfig()
    renderModel.overlay = ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/overlay.png").toFile())
    renderModel.overlayAlpha = 0.9f
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintMultiWindow() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintMultiWindowConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintMultiWindowSelected() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintMultiWindowConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    inspectorModel.setSelection(inspectorModel[VIEW3], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintMultiWindowRotated() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintMultiWindowConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    inspectorModel.setSelection(inspectorModel[VIEW3], SelectionOrigin.INTERNAL)
    renderModel.rotate(0.3, 0.2)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintMultiWindowDimBehind() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintMultiWindowConfig(WINDOW_MANAGER_FLAG_DIM_BEHIND)
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintMultiWindowDimBehindRotated() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintMultiWindowConfig(WINDOW_MANAGER_FLAG_DIM_BEHIND)
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    renderModel.rotate(0.3, 0.2)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithImagesBetweenChildren() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintImagesBetweenChildrenConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithImagesBetweenChildrenRotated() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintImagesBetweenChildrenConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    renderModel.layerSpacing = 60
    renderModel.rotate(0.3, 0.2)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithImagesBetweenChildrenRootSelected() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintImagesBetweenChildrenConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    renderModel.layerSpacing = 60
    renderModel.rotate(0.3, 0.2)
    inspectorModel.setSelection(inspectorModel[ROOT], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithRootImageOnly() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithRootImageOnlyConfig()
    renderSettings.drawLabel = false
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithRootImageOnlyRootSelected() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithRootImageOnlyConfig()
    renderSettings.drawLabel = false
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    inspectorModel.setSelection(inspectorModel[ROOT], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithRootImageOnlyView1Selected() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithRootImageOnlyConfig()
    renderSettings.drawLabel = false
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintTransformed() {
    val (_, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintTransformedConfig()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintTransformedView1Selected() {
    val (inspectorModel, _, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintTransformedConfig()
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintTransformedUntransformed() {
    val (inspectorModel, renderSettings, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintTransformedConfig()
    renderSettings.drawUntransformedBounds = true
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintTransformedOnlyUntransformed() {
    val (inspectorModel, renderSettings, _, renderLogic, renderImage, renderDimension, centerTransform) = createPaintTransformedConfig()
    renderSettings.drawUntransformedBounds = true
    renderSettings.drawBorders = false
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintTransformedOutsideRoot() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintTransformedOutsideRootConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintTransformedOutsideRootView1Selected() {
    val (inspectorModel, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintTransformedOutsideRootConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    inspectorModel.setSelection(inspectorModel[VIEW1], SelectionOrigin.INTERNAL)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintRound() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintRoundConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithChildrenOutsideParent() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithChildrenOutsideParentConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithChildrenOutsideParentRotated() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithChildrenOutsideParentConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    renderModel.layerSpacing = 30
    renderModel.rotate(0.3, 0.2)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  @Test
  fun testPaintWithChildAboveSibling() {
    val (_, _, renderModel, renderLogic, renderImage, renderDimension, centerTransform) = createPaintWithChildAboveSiblingConfig()
    treeSettings.hideSystemNodes = false
    // need to refresh to apply the treeSettings.hideSystemNodes
    renderModel.refresh()
    renderModel.layerSpacing = 30
    renderModel.rotate(0.3, 0.2)
    paint(renderImage, centerTransform, renderLogic, renderDimension)
    assertSimilar(renderImage, testName.methodName)
  }

  private fun paint(image: BufferedImage, transform: AffineTransform, renderLogic: RenderLogic, renderDimension: Dimension) {
    val graphics = image.createGraphics()
    // add a gray background
    graphics.fillRect(Rectangle(0, 0, renderDimension.width, renderDimension.height), Color(250, 250, 250))
    graphics.font = ImageDiffTestUtil.getDefaultFont()
    // add transform to center render in buffered image
    graphics.transform = transform

    renderLogic.renderImages(graphics)
    renderLogic.renderBorders(graphics, mock(), Color.BLACK)
    renderLogic.renderOverlay(graphics)
  }

  /**
   * Check that the generated [renderImage] is similar to the one stored on disk.
   * If the image stored on disk does not exist, it is created.
   */
  private fun assertSimilar(renderImage: BufferedImage, imageName: String) {
    val testDataPath = TEST_DATA_PATH.resolve(this.javaClass.simpleName)
    ImageDiffUtil.assertImageSimilar(TestUtils.resolveWorkspacePathUnchecked(testDataPath.resolve("$imageName.png").pathString), renderImage, DIFF_THRESHOLD)
  }

  /**
   * Re-used to generate all configs starting from an [InspectorModel] and [Dimension].
   */
  private fun createPaintConfig(inspectorModel: InspectorModel, renderDimension: Dimension): TestConfig {
    val renderModel = RenderModel(inspectorModel, treeSettings) { mock() }
    val renderLogic = RenderLogic(renderModel, renderSettings)

    // center the render in the buffered image
    val centerTransform = AffineTransform().apply {
      translate(renderDimension.width / 2.0, renderDimension.height / 2.0)
    }

    return TestConfig(
      inspectorModel,
      renderSettings,
      renderModel,
      renderLogic,
      BufferedImage(renderDimension.width, renderDimension.height, BufferedImage.TYPE_INT_ARGB),
      renderDimension,
      centerTransform
    )
  }

  private fun createPaintBordersConfig(): TestConfig {
    val inspectorModel = model {
      view(ROOT, 0, 0, 100, 150) {
        view(VIEW1, 10, 15, 25, 25) {
          image()
        }
        compose(COMPOSE1, "Text", composeCount = 15, x = 10, y = 50, width = 80, height = 50)
      }
    }

    val renderDimension = Dimension(120, 200)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintImagesConfig(): TestConfig {
    val image1 = ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image1.png").toFile())
    val image2 = ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image2.png").toFile())
    val image3 = ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image3.png").toFile())

    val inspectorModel = model {
      view(ROOT, 0, 0, 385, 504) {
        image(image1)
        view(VIEW1, 0, 0, 385, 385) {
          image(image2)
        }
        view(VIEW2, 96, 240, 193, 264) {
          image(image3)
        }
      }
    }

    val renderDimension = Dimension(500, 710)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintFoldConfig(width: Int, height: Int): TestConfig {
    val inspectorModel = model {
      view(ROOT, 0, 0, 20, 40) {
        view(VIEW1, 3, 3, 14, 14) {
          view(VIEW2, 6, 6, 8, 8)
          image()
        }
      }
    }

    val renderDimension = Dimension(width, height)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintWithHiddenSystemViews(): TestConfig {
    val inspectorModel = model {
      view(ROOT, 0, 0, 100, 150, layout = null) {
        view(VIEW1, 10, 15, 25, 25, layout = activityMain) {
          image()
        }
      }
    }

    val renderDimension = Dimension(120, 200)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintMultiWindowConfig(layoutFlag: Int = 0): TestConfig {
    val inspectorModel = model {
      view(ROOT, 0, 0, 100, 200) {
        view(VIEW1, 0, 0, 50, 50) {
          image()
        }
      }
    }

    // Second window. Root doesn't overlap with top of first window--verify they're on separate levels in the drawing.
    val window2 = window(VIEW2, VIEW2, 60, 60, 30, 30, layoutFlags = layoutFlag) {
      view(VIEW3, 70, 70, 10, 10)
    }

    inspectorModel.update(window2, listOf(ROOT, VIEW2), 0)

    val renderDimension = Dimension(200, 300)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintImagesBetweenChildrenConfig(): TestConfig {
    val image1 = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
    image1.graphics.run {
      color = Color.RED
      fillRect(10, 10, 20, 20)
    }
    val image2 = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
    image2.graphics.run {
      color = Color.BLUE
      fillRect(0, 0, 24, 24)
    }
    val image3 = BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB)
    image3.graphics.run {
      color = Color.GREEN
      fillRect(0, 0, 20, 20)
    }

    val inspectorModel = model {
      view(ROOT, 0, 0, 40, 40) {
        view(VIEW1, 0, 0, 40, 40) {
          image(image2)
        }
        image(image1)
        view(VIEW2, 20, 20, 20, 20) {
          image(image3)
        }
      }
    }

    val renderDimension = Dimension(120, 140)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintWithRootImageOnlyConfig(): TestConfig {
    val image1 = ImageIO.read(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/image1.png").toFile())
    val inspectorModel = model {
      view(ROOT, 0, 0, 327, 450) {
        image(image1)
        view(VIEW1, 0, 100, 85, 85)
        view(VIEW2, 100, 200, 93, 202)
      }
    }

    val renderDimension = Dimension(350, 450)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintTransformedConfig(): TestConfig {
    val image1 = BufferedImage(220, 220, BufferedImage.TYPE_INT_ARGB)
    (image1.graphics as Graphics2D).run {
      paint = GradientPaint(0f, 40f, Color.RED, 220f, 180f, Color.BLUE)
      fill(Polygon(intArrayOf(0, 180, 220, 40), intArrayOf(40, 0, 180, 220), 4))
    }

    val inspectorModel = model {
      view(ROOT, 0, 0, 400, 600) {
        view(VIEW1, 50, 100, 300, 300, bounds = Polygon(intArrayOf(90, 270, 310, 130), intArrayOf(180, 140, 320, 360), 4)) {
          image(image1)
        }
      }
    }

    val renderDimension = Dimension(400, 600)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintTransformedOutsideRootConfig(): TestConfig {
    val image1 = BufferedImage(80, 150, BufferedImage.TYPE_INT_ARGB)
    (image1.graphics as Graphics2D).run {
      paint = GradientPaint(0f, 0f, Color.RED, 80f, 100f, Color.BLUE)
      fillRect(0, 0, 80, 100)
    }

    val inspectorModel = model {
      view(ROOT, 0, 0, 100, 100) {
        view(VIEW1, 20, 20, 60, 60, bounds = Polygon(intArrayOf(-20, 80, 80, -20), intArrayOf(-50, -50, 150, 150), 4)) {
          image(image1)
        }
      }
    }

    val renderDimension = Dimension(200, 200)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintRoundConfig(): TestConfig {
    val screenshot = Screenshot("wear.png", BitmapType.RGB_565)

    val viewLayoutEvent = LayoutInspectorViewProtocol.LayoutEvent.newBuilder().apply {
      screenshotBuilder.apply {
        type = LayoutInspectorViewProtocol.Screenshot.Type.BITMAP
        bytes = ByteString.copyFrom(screenshot.bytes)
      }
    }.build()

    val inspectorModel = model {}
    val folderConfiguration = FolderConfiguration().apply {
      screenRoundQualifier = ScreenRoundQualifier(ScreenRound.ROUND)
      screenWidthQualifier = ScreenWidthQualifier.getQualifier("454")
      screenHeightQualifier = ScreenHeightQualifier.getQualifier("454")
      densityQualifier = DensityQualifier(Density.MEDIUM)
    }

    val window = ViewAndroidWindow(
      projectRule.project, mock(),
      view(1, 0, 0, 454, 454) {
        view(2, 10, 10, 100, 100)
        view(3, 300, 400, 50, 50)
      },
      viewLayoutEvent, folderConfiguration, { false }, {}
    )
    inspectorModel.update(window, listOf(1L), 2)
    inspectorModel.windows[1L]?.refreshImages(1.0)

    val renderDimension = Dimension(454, 454)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintWithChildrenOutsideParentConfig(): TestConfig {
    val inspectorModel = model {
      view(ROOT, 0, 0, 20, 40) {
        view(VIEW1, 0, 0, 20, 40)
        image()
        view(VIEW2, -23, 0, 20, 40)
        view(VIEW3, 0, 0, 20, 40)
      }
    }

    val renderDimension = Dimension(90, 70)
    return createPaintConfig(inspectorModel, renderDimension)
  }

  private fun createPaintWithChildAboveSiblingConfig(): TestConfig {
    val inspectorModel = model {
      view(ROOT, 0, 0, 20, 40) {
        view(VIEW1, 0, 0, 20, 20) {
          view(VIEW2, 0, 0, 20, 40)
        }
        view(VIEW3, 0, 20, 20, 20)
      }
    }

    val renderDimension = Dimension(70, 70)
    return createPaintConfig(inspectorModel, renderDimension)
  }
}
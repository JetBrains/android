/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.SYSTEM_PKG
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.FLAG_SYSTEM_DEFINED
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.GetComposablesResult
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableRoot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewBounds
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewQuad
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewRect
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.GRAMMATICAL_GENDER_FEMININE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_HDR_YES
import com.android.tools.idea.layoutinspector.resource.COLOR_MODE_WIDE_COLOR_GAMUT_YES
import com.android.tools.idea.layoutinspector.resource.KEYBOARDHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.KEYBOARD_QWERTY
import com.android.tools.idea.layoutinspector.resource.NAVIGATIONHIDDEN_NO
import com.android.tools.idea.layoutinspector.resource.NAVIGATION_WHEEL
import com.android.tools.idea.layoutinspector.resource.ORIENTATION_PORTRAIT
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LAYOUTDIR_RTL
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_LONG_YES
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_ROUND_YES
import com.android.tools.idea.layoutinspector.resource.SCREENLAYOUT_SIZE_SMALL
import com.android.tools.idea.layoutinspector.resource.TOUCHSCREEN_STYLUS
import com.android.tools.idea.layoutinspector.resource.UI_MODE_NIGHT_NO
import com.android.tools.idea.layoutinspector.resource.UI_MODE_TYPE_NORMAL
import com.android.tools.idea.layoutinspector.setApplicationIdForTest
import com.android.tools.idea.layoutinspector.skia.ParsingFailedException
import com.android.tools.idea.layoutinspector.skia.SkiaParser
import com.android.tools.idea.layoutinspector.skia.UnsupportedPictureVersionException
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot.Type.BITMAP
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.BitmapType
import com.android.tools.layoutinspector.InvalidPictureException
import com.android.tools.layoutinspector.SkiaViewNode
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Image
import java.awt.Polygon
import kotlinx.coroutines.runBlocking
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppInspectionTreeLoaderTest {

  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  private val sample565 = Screenshot("partiallyTransparentImage.png", BitmapType.RGB_565)
  private val sample8888 = Screenshot("partiallyTransparentImage.png", BitmapType.ABGR_8888)

  private val themes =
    """
    <resources xmlns:tools="http://schemas.android.com/tools">
        <style name="Theme.BasicViews" parent="android:Theme.Dark" />
    </resources>
"""
      .trimIndent()

  @Before
  fun before() {
    projectRule.fixture.addFileToProject("res/values/themes.xml", themes)
    AndroidFacet.getInstance(projectRule.module)!!.setApplicationIdForTest("com.example")
  }

  /**
   * Generate fake data containing hand-crafted layout information that can be used for generating
   * trees.
   */
  private fun createFakeData(
    screenshotType: LayoutInspectorViewProtocol.Screenshot.Type =
      LayoutInspectorViewProtocol.Screenshot.Type.SKP,
    bitmapType: BitmapType = BitmapType.RGB_565,
    pendingRecompositionCountReset: Boolean = true,
    hasScreenshot: Boolean = true,
  ): ViewLayoutInspectorClient.Data {
    val viewLayoutEvent =
      LayoutInspectorViewProtocol.LayoutEvent.newBuilder()
        .apply {
          ViewString(1, "en-us")
          ViewString(2, "com.example")
          ViewString(3, "MyViewClass1")
          ViewString(4, "MyViewClass2")
          ViewString(5, "androidx.compose.ui.platform")
          ViewString(6, "ComposeView")
          ViewString(7, "style")
          ViewString(8, "Theme.BasicViews")

          appContextBuilder.apply {
            configurationBuilder.apply {
              countryCode = 310
              networkCode = 410
              screenLayout =
                SCREENLAYOUT_SIZE_SMALL or
                  SCREENLAYOUT_LONG_YES or
                  SCREENLAYOUT_LAYOUTDIR_RTL or
                  SCREENLAYOUT_ROUND_YES
              colorMode = COLOR_MODE_WIDE_COLOR_GAMUT_YES or COLOR_MODE_HDR_YES
              touchScreen = TOUCHSCREEN_STYLUS
              keyboard = KEYBOARD_QWERTY
              keyboardHidden = KEYBOARDHIDDEN_NO
              hardKeyboardHidden = KEYBOARDHIDDEN_NO
              navigation = NAVIGATION_WHEEL
              navigationHidden = NAVIGATIONHIDDEN_NO
              uiMode = UI_MODE_TYPE_NORMAL or UI_MODE_NIGHT_NO
              smallestScreenWidthDp = 200
              density = 560
              orientation = ORIENTATION_PORTRAIT
              screenWidthDp = 480
              screenHeightDp = 800
              grammaticalGender = GRAMMATICAL_GENDER_FEMININE
            }
            mainDisplayHeight = 800
            mainDisplayWidth = 480
            themeBuilder.apply {
              type = 7
              namespace = 2
              name = 8
            }
          }

          rootView = ViewNode {
            id = 1
            packageName = 2
            className = 3
            bounds = ViewBounds(ViewRect(sample565.image.width, sample565.image.height))

            ViewNode {
              id = 2
              packageName = 2
              className = 4
              bounds = ViewBounds(ViewRect(10, 10, 50, 100))

              ViewNode {
                id = 3
                packageName = 2
                className = 3
                bounds = ViewBounds(ViewRect(20, 20, 20, 50))
              }
            }

            ViewNode {
              id = 4
              packageName = 2
              className = 4
              bounds =
                ViewBounds(ViewRect(30, 120, 40, 50), ViewQuad(25, 125, 75, 127, 23, 250, 78, 253))
            }

            ViewNode {
              id = 5
              packageName = 5
              className = 6
              bounds = ViewBounds(ViewRect(300, 200))
            }
          }

          if (hasScreenshot) {
            screenshotBuilder.apply {
              type = screenshotType
              bytes =
                ByteString.copyFrom(Screenshot("partiallyTransparentImage.png", bitmapType).bytes)
            }
          }
        }
        .build()

    val nestedFlag =
      LayoutInspectorComposeProtocol.ComposableNode.Flags.NESTED_SINGLE_CHILDREN_VALUE
    val composablesResponse =
      LayoutInspectorComposeProtocol.GetComposablesResponse.newBuilder()
        .apply {
          ComposableString(1, "com.example")
          ComposableString(2, "File1.kt")
          ComposableString(3, "File2.kt")
          ComposableString(4, "Surface")
          ComposableString(5, "Button")
          ComposableString(6, "Text")
          ComposableString(7, "BasicText")
          ComposableString(8, "BasicText.kt")
          ComposableString(9, "Recursive")

          ComposableRoot {
            viewId = 5
            ComposableNode {
              id = -2 // -1 is reserved by inspectorModel
              packageHash = 1
              filename = 2
              name = 4
              recomposeCount = 2
              recomposeSkips = 5

              ComposableNode {
                id = -3
                packageHash = 1
                filename = 2
                name = 5
                recomposeCount = 3
                recomposeSkips = 5

                ComposableNode {
                  id = -4
                  packageHash = 1
                  filename = 2
                  name = 6
                  recomposeCount = 4
                  recomposeSkips = 5

                  ComposableNode {
                    id = -5
                    packageHash = SYSTEM_PKG
                    filename = 8
                    name = 7
                    flags = FLAG_SYSTEM_DEFINED
                    // These recomposition numbers will be ignored because this is a system node
                    recomposeCount = 4
                    recomposeSkips = 5
                  }
                }
              }
            }
            ComposableNode {
              id = -6
              packageHash = 1
              filename = 3
              name = 9
              flags = nestedFlag

              ComposableNode {
                id = -7
                packageHash = 1
                filename = 3
                name = 9
              }
              ComposableNode {
                id = -8
                packageHash = 1
                filename = 3
                name = 9
              }
              ComposableNode {
                id = -9
                packageHash = 1
                filename = 3
                name = 9
              }
              ComposableNode {
                id = -10
                packageHash = 1
                filename = 2
                name = 5
                recomposeCount = 4
                recomposeSkips = 4

                ComposableNode {
                  id = -11
                  packageHash = 1
                  filename = 3
                  name = 6
                  recomposeCount = 5
                  recomposeSkips = 5
                }
              }
            }
          }
        }
        .build()

    return ViewLayoutInspectorClient.Data(
      11,
      listOf(123, 456),
      viewLayoutEvent,
      GetComposablesResult(composablesResponse, pendingRecompositionCountReset),
    )
  }

  @Test
  fun testLoad() {
    runLoadTest(pendingRecompositionCountReset = false)
  }

  @Test
  fun testLoadAfterReset() {
    runLoadTest(pendingRecompositionCountReset = true)
  }

  private fun runLoadTest(pendingRecompositionCountReset: Boolean) {
    val image1: Image = mock()
    val image2: Image = mock()
    val image3: Image = mock()
    val image4: Image = mock()
    val image5: Image = mock()

    val skiaResponse =
      SkiaViewNode(
        1,
        listOf(
          SkiaViewNode(1, image1),
          SkiaViewNode(
            2,
            listOf(SkiaViewNode(2, image2), SkiaViewNode(3, listOf(SkiaViewNode(3, image3)))),
          ),
          SkiaViewNode(4, listOf(SkiaViewNode(4, image4))),
          SkiaViewNode(5, listOf(SkiaViewNode(5, image5))),
        ),
      )

    val skiaParser: SkiaParser = mock()
    whenever(
        skiaParser.getViewTree(
          eq(sample565.bytes),
          argThat { req -> req.map { it.id }.sorted() == listOf(1L, 2L, 3L, 4L, 5L) },
          any(),
          any(),
        )
      )
      .thenReturn(skiaResponse)

    var loggedEvent: DynamicLayoutInspectorEventType? = null
    val treeLoader =
      AppInspectionTreeLoader(
        NotificationModel(projectRule.project),
        // Initial event is only ever logged one time
        logEvent = {
          assertThat(loggedEvent).isNull()
          loggedEvent = it
        },
        skiaParser,
      )

    val data = createFakeData(pendingRecompositionCountReset = pendingRecompositionCountReset)
    val lookup = ResourceLookup(projectRule.project)
    val (window, generation) =
      treeLoader.loadComponentTree(data, lookup, MODERN_DEVICE.createProcess())!!
    assertThat(data.generation).isEqualTo(generation)

    runBlocking { window!!.refreshImages(1.0) }

    ViewNode.readAccess {
      val tree = window!!.root
      assertThat(tree.drawId).isEqualTo(1)
      assertThat(tree.layoutBounds.x).isEqualTo(0)
      assertThat(tree.layoutBounds.y).isEqualTo(0)
      assertThat(tree.layoutBounds.width).isEqualTo(sample565.image.width)
      assertThat(tree.layoutBounds.height).isEqualTo(sample565.image.height)
      assertThat(tree.qualifiedName).isEqualTo("com.example.MyViewClass1")
      assertThat((tree.drawChildren[0] as DrawViewImage).image).isEqualTo(image1)
      assertThat(tree.children.map { it.drawId }).containsExactly(2L, 4L, 5L).inOrder()

      val node2 = tree.children[0]
      assertThat(node2.drawId).isEqualTo(2)
      assertThat(node2.layoutBounds.x).isEqualTo(10)
      assertThat(node2.layoutBounds.y).isEqualTo(10)
      assertThat(node2.layoutBounds.width).isEqualTo(50)
      assertThat(node2.layoutBounds.height).isEqualTo(100)
      assertThat(node2.qualifiedName).isEqualTo("com.example.MyViewClass2")
      assertThat((node2.drawChildren[0] as DrawViewImage).image).isEqualTo(image2)
      assertThat(node2.children.map { it.drawId }).containsExactly(3L)

      val node3 = node2.children[0]
      assertThat(node3.drawId).isEqualTo(3)
      assertThat(node3.layoutBounds.x).isEqualTo(20)
      assertThat(node3.layoutBounds.y).isEqualTo(20)
      assertThat(node3.layoutBounds.width).isEqualTo(20)
      assertThat(node3.layoutBounds.height).isEqualTo(50)
      assertThat(node3.qualifiedName).isEqualTo("com.example.MyViewClass1")
      assertThat((node3.drawChildren[0] as DrawViewImage).image).isEqualTo(image3)
      assertThat(node3.children).isEmpty()

      val node4 = tree.children[1]
      assertThat(node4.drawId).isEqualTo(4)
      assertThat(node4.layoutBounds.x).isEqualTo(30)
      assertThat(node4.layoutBounds.y).isEqualTo(120)
      assertThat(node4.layoutBounds.width).isEqualTo(40)
      assertThat(node4.layoutBounds.height).isEqualTo(50)
      assertThat(node4.qualifiedName).isEqualTo("com.example.MyViewClass2")
      assertThat((node4.drawChildren[0] as DrawViewImage).image).isEqualTo(image4)
      assertThat(node4.children).isEmpty()
      assertThat((node4.renderBounds as Polygon).xpoints).isEqualTo(intArrayOf(25, 75, 23, 78))
      assertThat((node4.renderBounds as Polygon).ypoints).isEqualTo(intArrayOf(125, 127, 250, 253))

      val node6 = tree.children[2]
      assertThat(node6.drawId).isEqualTo(5)
      assertThat(node6.layoutBounds.x).isEqualTo(0)
      assertThat(node6.layoutBounds.y).isEqualTo(0)
      assertThat(node6.layoutBounds.width).isEqualTo(300)
      assertThat(node6.layoutBounds.height).isEqualTo(200)
      assertThat(node6.qualifiedName).isEqualTo("androidx.compose.ui.platform.ComposeView")
      assertThat((node6.drawChildren[0] as DrawViewImage).image).isEqualTo(image5)
      assertThat(node6.children.map { it.drawId }).containsExactly(-2L, -6L)

      val cNode2 = node6.children[0] as ComposeViewNode
      assertThat(cNode2.drawId).isEqualTo(-2)
      assertThat(cNode2.qualifiedName).isEqualTo("Surface")
      assertThat(cNode2.recompositions.count)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 2)
      assertThat(cNode2.recompositions.skips)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 5)
      assertThat(cNode2.children.map { it.drawId }).containsExactly(-3L)

      val cNode3 = cNode2.children[0] as ComposeViewNode
      assertThat(cNode3.drawId).isEqualTo(-3)
      assertThat(cNode3.qualifiedName).isEqualTo("Button")
      assertThat(cNode3.recompositions.count)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 3)
      assertThat(cNode3.recompositions.skips)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 5)
      assertThat(cNode3.children.map { it.drawId }).containsExactly(-4L)

      val cNode4 = cNode3.children[0] as ComposeViewNode
      assertThat(cNode4.drawId).isEqualTo(-4)
      assertThat(cNode4.qualifiedName).isEqualTo("Text")
      assertThat(cNode4.recompositions.count)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 4)
      assertThat(cNode4.recompositions.skips)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 5)
      assertThat(cNode4.children.map { it.drawId }).containsExactly(-5L)

      val cNode5 = cNode4.children[0] as ComposeViewNode
      assertThat(cNode5.isSystemNode).isTrue()
      assertThat(cNode5.drawId).isEqualTo(-5)
      assertThat(cNode5.qualifiedName).isEqualTo("BasicText")
      assertThat(cNode5.recompositions.count).isEqualTo(0)
      assertThat(cNode5.recompositions.skips).isEqualTo(0)
      assertThat(cNode5.children).isEmpty()

      val cNode6 = node6.children[1] as ComposeViewNode
      assertThat(cNode6.isSystemNode).isFalse()
      assertThat(cNode6.drawId).isEqualTo(-6)
      assertThat(cNode6.qualifiedName).isEqualTo("Recursive")
      assertThat(cNode6.recompositions.count).isEqualTo(0)
      assertThat(cNode6.recompositions.skips).isEqualTo(0)
      assertThat(cNode6.children).hasSize(1)

      val cNode7 = cNode6.children[0] as ComposeViewNode
      assertThat(cNode7.isSystemNode).isFalse()
      assertThat(cNode7.drawId).isEqualTo(-7)
      assertThat(cNode7.qualifiedName).isEqualTo("Recursive")
      assertThat(cNode7.recompositions.count).isEqualTo(0)
      assertThat(cNode7.recompositions.skips).isEqualTo(0)
      assertThat(cNode7.children).hasSize(1)

      val cNode8 = cNode7.children[0] as ComposeViewNode
      assertThat(cNode8.isSystemNode).isFalse()
      assertThat(cNode8.drawId).isEqualTo(-8)
      assertThat(cNode8.qualifiedName).isEqualTo("Recursive")
      assertThat(cNode8.recompositions.count).isEqualTo(0)
      assertThat(cNode8.recompositions.skips).isEqualTo(0)
      assertThat(cNode8.children).hasSize(1)

      val cNode9 = cNode8.children[0] as ComposeViewNode
      assertThat(cNode9.isSystemNode).isFalse()
      assertThat(cNode9.drawId).isEqualTo(-9)
      assertThat(cNode9.qualifiedName).isEqualTo("Recursive")
      assertThat(cNode9.recompositions.count).isEqualTo(0)
      assertThat(cNode9.recompositions.skips).isEqualTo(0)
      assertThat(cNode9.children).hasSize(1)

      val cNode10 = cNode9.children[0] as ComposeViewNode
      assertThat(cNode10.drawId).isEqualTo(-10)
      assertThat(cNode10.qualifiedName).isEqualTo("Button")
      assertThat(cNode10.recompositions.count)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 4)
      assertThat(cNode10.recompositions.skips)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 4)
      assertThat(cNode10.children).hasSize(1)

      val cNode11 = cNode10.children[0] as ComposeViewNode
      assertThat(cNode11.drawId).isEqualTo(-11)
      assertThat(cNode11.qualifiedName).isEqualTo("Text")
      assertThat(cNode11.recompositions.count)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 5)
      assertThat(cNode11.recompositions.skips)
        .isEqualTo(if (pendingRecompositionCountReset) 0 else 5)
      assertThat(cNode11.children).isEmpty()

      assertThat(loggedEvent).isEqualTo(DynamicLayoutInspectorEventType.INITIAL_RENDER)

      assertThat(lookup.hasResolver).isTrue()
      assertThat(lookup.defaultTheme?.resourceUrl?.toString()).isEqualTo("@style/Theme.BasicViews")
      assertThat(lookup.dpi).isEqualTo(560)
      assertThat(lookup.screenDimension).isEqualTo(Dimension(480, 800))
    }
  }

  private fun assertExpectedErrorIfSkiaRespondsWith(msg: String, skiaAnswer: () -> Any) {
    val skiaParser: SkiaParser = mock()
    whenever(skiaParser.getViewTree(eq(sample565.bytes), any(), any(), any())).thenAnswer {
      skiaAnswer()
    }

    val notificationModel = NotificationModel(projectRule.project)
    val treeLoader =
      AppInspectionTreeLoader(
        notificationModel,
        logEvent = { fail() }, // Metrics shouldn't be logged until we come back with a screenshot
        skiaParser,
      )
    val (window, _) =
      treeLoader.loadComponentTree(
        createFakeData(),
        ResourceLookup(projectRule.project),
        MODERN_DEVICE.createProcess(),
      )!!
    runBlocking { window!!.refreshImages(1.0) }
    invokeAndWaitIfNeeded { UIUtil.dispatchAllInvocationEvents() }

    val notification1 = notificationModel.notifications.single()
    assertThat(notification1.message).isEqualTo(msg)
  }

  @Test
  fun testUnsupportedSkpVersion() {
    assertExpectedErrorIfSkiaRespondsWith(
      "No renderer supporting SKP version 123 found. Rotation disabled."
    ) {
      throw UnsupportedPictureVersionException(123)
    }
  }

  @Test
  fun testSkpParsingFailed() {
    assertExpectedErrorIfSkiaRespondsWith(
      "Invalid picture data received from device. Rotation disabled."
    ) {
      throw ParsingFailedException()
    }
  }

  @Test
  fun testInvalidSkp() {
    assertExpectedErrorIfSkiaRespondsWith(
      "Invalid picture data received from device. Rotation disabled."
    ) {
      throw InvalidPictureException()
    }
  }

  @Test
  fun testGeneralException() {
    assertExpectedErrorIfSkiaRespondsWith("Problem launching renderer. Rotation disabled.") {
      throw Exception()
    }
  }

  @Test
  fun testCanProcessBitmapScreenshots() = runBlocking {
    val skiaParser: SkiaParser = mock()
    whenever(skiaParser.getViewTree(any(), any(), any(), any()))
      .thenThrow(AssertionError("SKIA not used in bitmap mode"))
    val treeLoader =
      AppInspectionTreeLoader(
        NotificationModel(projectRule.project),
        logEvent = {
          assertThat(it).isEqualTo(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
        },
        skiaParser,
      )

    val data = createFakeData(BITMAP)
    val (window, generation) =
      treeLoader.loadComponentTree(
        data,
        ResourceLookup(projectRule.project),
        MODERN_DEVICE.createProcess(),
      )!!
    assertThat(data.generation).isEqualTo(generation)
    window!!.refreshImages(1.0)

    val resultImage = ViewNode.readAccess { (window.root.drawChildren[0] as DrawViewImage).image }
    ImageDiffUtil.assertImageSimilar("image1.png", sample565.image, resultImage, 0.01)

    val data2 = createFakeData(BITMAP, bitmapType = BitmapType.ARGB_8888)
    val (window2, _) =
      treeLoader.loadComponentTree(
        data2,
        ResourceLookup(projectRule.project),
        MODERN_DEVICE.createProcess(),
      )!!
    window2!!.refreshImages(1.0)

    val resultImage2 = ViewNode.readAccess { (window2.root.drawChildren[0] as DrawViewImage).image }
    ImageDiffUtil.assertImageSimilar("image1.png", sample8888.image, resultImage2, 0.01)
  }

  @Test
  fun testCanProcessWithoutScreenshot() {
    val skiaParser: SkiaParser = mock()
    whenever(skiaParser.getViewTree(any(), any(), any(), any()))
      .thenThrow(AssertionError("SKIA not used in bitmap mode"))
    val treeLoader =
      AppInspectionTreeLoader(
        NotificationModel(projectRule.project),
        logEvent = {
          assertThat(it).isEqualTo(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
        },
        skiaParser,
      )

    val data = createFakeData(hasScreenshot = false)
    val (window, generation) =
      treeLoader.loadComponentTree(
        data,
        ResourceLookup(projectRule.project),
        MODERN_DEVICE.createProcess(),
      )!!
    assertThat(data.generation).isEqualTo(generation)
    runBlocking { window!!.refreshImages(1.0) }

    val hasDrawViewImage =
      ViewNode.readAccess {
        (window!!.root.drawChildren.filterIsInstance<DrawViewImage>().isNotEmpty())
      }
    assertThat(hasDrawViewImage).isFalse()
  }
}

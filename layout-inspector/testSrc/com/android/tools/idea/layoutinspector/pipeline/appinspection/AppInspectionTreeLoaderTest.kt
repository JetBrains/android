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
import com.android.tools.idea.layoutinspector.DEVICE_1
import com.android.tools.idea.layoutinspector.SYSTEM_PKG
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.FLAG_SYSTEM_DEFINED
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
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot.Type.BITMAP
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.BitmapType
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import kotlinx.coroutines.runBlocking
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import org.jetbrains.android.facet.AndroidFacet
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

  /** Generate fake data containing hand-crafted layout information that can be used for generating trees. */
  private fun createFakeData(
    screenshotType: LayoutInspectorViewProtocol.Screenshot.Type = LayoutInspectorViewProtocol.Screenshot.Type.SKP,
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
              screenLayout = SCREENLAYOUT_SIZE_SMALL or SCREENLAYOUT_LONG_YES or SCREENLAYOUT_LAYOUTDIR_RTL or SCREENLAYOUT_ROUND_YES
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
            val display = LayoutInspectorViewProtocol.Display.newBuilder().setWidth(480).setHeight(800).setOrientation(90).setId(1).build()
            addDisplayInfo(display)
            themeBuilder.apply {
              type = 7
              namespace = 2
              name = 8
            }
          }

          rootView =
            LayoutInspectorViewProtocol.RootView.newBuilder()
              .apply {
                node = ViewNode {
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
                    bounds = ViewBounds(ViewRect(30, 120, 40, 50), ViewQuad(25, 125, 75, 127, 23, 250, 78, 253))
                  }

                  ViewNode {
                    id = 5
                    packageName = 5
                    className = 6
                    bounds = ViewBounds(ViewRect(300, 200))
                  }
                }
              }
              .build()

          if (hasScreenshot) {
            screenshotBuilder.apply {
              type = screenshotType
              bytes = ByteString.copyFrom(Screenshot("partiallyTransparentImage.png", bitmapType).bytes)
            }
          }
        }
        .build()

    val nestedFlag = LayoutInspectorComposeProtocol.ComposableNode.Flags.NESTED_SINGLE_CHILDREN_VALUE
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
  fun testCanProcessBitmapScreenshots() = runBlocking {
    val treeLoader =
      AppInspectionTreeLoader(logEvent = { assertThat(it).isEqualTo(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS) })

    val data = createFakeData(BITMAP)
    val (window, generation) = treeLoader.loadComponentTree(data, ResourceLookup(projectRule.project), DEVICE_1.createProcess())!!
    assertThat(data.generation).isEqualTo(generation)
    window!!.refreshImages(1.0)

    val resultImage = window.image!!
    ImageDiffUtil.assertImageSimilar("image1.png", sample565.image, resultImage, 0.01)

    val data2 = createFakeData(BITMAP, bitmapType = BitmapType.ARGB_8888)
    val (window2, _) = treeLoader.loadComponentTree(data2, ResourceLookup(projectRule.project), DEVICE_1.createProcess())!!
    window2!!.refreshImages(1.0)

    val resultImage2 = window2.image!!
    ImageDiffUtil.assertImageSimilar("image1.png", sample8888.image, resultImage2, 0.01)
  }

  @Test
  fun testCanProcessWithoutScreenshot() {
    val treeLoader =
      AppInspectionTreeLoader(logEvent = { assertThat(it).isEqualTo(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS) })

    val data = createFakeData(hasScreenshot = false)
    val (window, generation) = treeLoader.loadComponentTree(data, ResourceLookup(projectRule.project), DEVICE_1.createProcess())!!
    assertThat(data.generation).isEqualTo(generation)
    runBlocking { window!!.refreshImages(1.0) }

    assertThat(window!!.image).isNull()
  }
}

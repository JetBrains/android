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

import com.android.io.readImage
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.layoutinspector.skia.SkiaParser
import com.android.tools.idea.layoutinspector.skia.UnsupportedPictureVersionException
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableRoot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewBounds
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewQuad
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewRect
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ViewString
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.skia.ParsingFailedException
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.layoutinspector.InvalidPictureException
import com.android.tools.layoutinspector.LayoutInspectorUtils
import com.android.tools.layoutinspector.SkiaViewNode
import com.android.tools.layoutinspector.toBytes
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.testFramework.ProjectRule
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import java.awt.Image
import java.awt.Polygon
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol as ComposeProtocol
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class AppInspectionTreeLoaderTest {

  /**
   * Process a target image png file and create the data that normally would have been generated on a target device.
   */
  private class Screenshot(filename: String) {
    val image: BufferedImage
    val bytes: ByteArray

    init {
      val origImage = getWorkspaceRoot().resolve("$TEST_DATA_PATH/$filename").readImage()
      image = LayoutInspectorUtils.createImage565(ByteBuffer.allocate(origImage.width * origImage.height * 2), origImage.width,
                                                  origImage.height)
      val graphics = image.graphics
      graphics.drawImage(origImage, 0, 0, null)
      val dataElements = image.raster.getDataElements(0, 0, image.width, image.height,
                                                      ShortArray(image.width * image.height)) as ShortArray
      val imageBytes = ArrayList<Byte>(image.width * image.height * 2 + 8)
      dataElements.flatMapTo(imageBytes) { listOf((it.toInt() and 0xFF).toByte(), (it.toInt() ushr 8).toByte()) }
      bytes = (image.width.toBytes().asList() + image.height.toBytes().asList() + imageBytes).toByteArray().compress()
    }
  }

  @get:Rule
  val projectRule = ProjectRule()

  private val sample = Screenshot("image1.png")

  /**
   * Generate fake data containing hand-crafted layout information that can be used for
   * generating trees.
   */
  private fun createFakeData(
    screenshotType: ViewProtocol.Screenshot.Type = ViewProtocol.Screenshot.Type.SKP)
    : ViewLayoutInspectorClient.Data {
    val viewLayoutEvent = ViewProtocol.LayoutEvent.newBuilder().apply {
      ViewString(1, "en-us")
      ViewString(2, "com.example")
      ViewString(3, "MyViewClass1")
      ViewString(4, "MyViewClass2")
      ViewString(5, "androidx.compose.ui.platform")
      ViewString(6, "ComposeView")

      appContextBuilder.apply {
        apiLevel = 29
        configurationBuilder.apply {
          countryCode = 1
        }
      }

      rootView = ViewNode {
        id = 1
        packageName = 2
        className = 3
        bounds = ViewBounds(
          ViewRect(sample.image.width, sample.image.height))

        ViewNode {
          id = 2
          packageName = 2
          className = 4
          bounds = ViewBounds(
            ViewRect(10, 10, 50, 100))

          ViewNode {
            id = 3
            packageName = 2
            className = 3
            bounds = ViewBounds(
              ViewRect(20, 20, 20, 50))
          }
        }

        ViewNode {
          id = 4
          packageName = 2
          className = 4
          bounds = ViewBounds(
            ViewRect(30, 120, 40, 50),
            ViewQuad(25, 125, 75, 127, 23, 250, 78, 253))
        }

        ViewNode {
          id = 5
          packageName = 5
          className = 6
          bounds = ViewBounds(ViewRect(300, 200))
        }
      }

      screenshotBuilder.apply {
        type = screenshotType
        bytes = ByteString.copyFrom(sample.bytes)
      }
    }.build()

    val composablesResponse = ComposeProtocol.GetComposablesResponse.newBuilder().apply {
      ComposableString(1, "com.example")
      ComposableString(2, "File1.kt")
      ComposableString(3, "File2.kt")
      ComposableString(4, "Surface")
      ComposableString(5, "Button")
      ComposableString(6, "Text")

      ComposableRoot {
        viewId = 5
        ComposableNode {
          id = -2 // -1 is reserved by inspectorModel
          packageHash = 1
          filename = 2
          name = 4

          ComposableNode {
            id = -3
            packageHash = 1
            filename = 2
            name = 5

            ComposableNode {
              id = -4
              packageHash = 1
              filename = 2
              name = 6
            }
          }
        }
        ComposableNode {
          id = -5
          packageHash = 1
          filename = 3
          name = 6
        }
      }
    }.build()

    return ViewLayoutInspectorClient.Data(
      11,
      listOf(123, 456),
      viewLayoutEvent,
      composablesResponse
    )
  }

  @Test
  fun testLoad() {
    val image1: Image = mock()
    val image2: Image = mock()
    val image3: Image = mock()
    val image4: Image = mock()
    val image5: Image = mock()

    val skiaResponse = SkiaViewNode(1, listOf(
      SkiaViewNode(1, image1),
      SkiaViewNode(2, listOf(
        SkiaViewNode(2, image2),
        SkiaViewNode(3, listOf(
          SkiaViewNode(3, image3)
        ))
      )),
      SkiaViewNode(4, listOf(
        SkiaViewNode(4, image4)
      )),
      SkiaViewNode(5, listOf(
        SkiaViewNode(5, image5)
      ))
    ))

    val skiaParser: SkiaParser = mock()
    `when`(
      skiaParser.getViewTree(eq(sample.bytes), argThat { req -> req.map { it.id }.sorted() == listOf(1L, 2L, 3L, 4L, 5L) }, any(), any()))
      .thenReturn(skiaResponse)

    var loggedEvent: DynamicLayoutInspectorEventType? = null
    val treeLoader = AppInspectionTreeLoader(
      projectRule.project,
      // Initial event is only ever logged one time
      logEvent = { assertThat(loggedEvent).isNull(); loggedEvent = it },
      skiaParser
    )

    val data = createFakeData()
    val (window, generation) = treeLoader.loadComponentTree(data, ResourceLookup(projectRule.project))!!
    assertThat(data.generation).isEqualTo(generation)

    window!!.refreshImages(1.0)

    val tree = window.root
    assertThat(tree.drawId).isEqualTo(1)
    assertThat(tree.x).isEqualTo(0)
    assertThat(tree.y).isEqualTo(0)
    assertThat(tree.width).isEqualTo(sample.image.width)
    assertThat(tree.height).isEqualTo(sample.image.height)
    assertThat(tree.qualifiedName).isEqualTo("com.example.MyViewClass1")
    ViewNode.readDrawChildren { drawChildren -> assertThat((tree.drawChildren()[0] as DrawViewImage).image).isEqualTo(image1) }
    assertThat(tree.children.map { it.drawId }).containsExactly(2L, 4L, 5L).inOrder()

    val node2 = tree.children[0]
    assertThat(node2.drawId).isEqualTo(2)
    assertThat(node2.x).isEqualTo(10)
    assertThat(node2.y).isEqualTo(10)
    assertThat(node2.width).isEqualTo(50)
    assertThat(node2.height).isEqualTo(100)
    assertThat(node2.qualifiedName).isEqualTo("com.example.MyViewClass2")
    ViewNode.readDrawChildren { drawChildren -> assertThat((node2.drawChildren()[0] as DrawViewImage).image).isEqualTo(image2) }
    assertThat(node2.children.map { it.drawId }).containsExactly(3L)

    val node3 = node2.children[0]
    assertThat(node3.drawId).isEqualTo(3)
    assertThat(node3.x).isEqualTo(20)
    assertThat(node3.y).isEqualTo(20)
    assertThat(node3.width).isEqualTo(20)
    assertThat(node3.height).isEqualTo(50)
    assertThat(node3.qualifiedName).isEqualTo("com.example.MyViewClass1")
    ViewNode.readDrawChildren { drawChildren -> assertThat((node3.drawChildren()[0] as DrawViewImage).image).isEqualTo(image3) }
    assertThat(node3.children).isEmpty()

    val node4 = tree.children[1]
    assertThat(node4.drawId).isEqualTo(4)
    assertThat(node4.x).isEqualTo(30)
    assertThat(node4.y).isEqualTo(120)
    assertThat(node4.width).isEqualTo(40)
    assertThat(node4.height).isEqualTo(50)
    assertThat(node4.qualifiedName).isEqualTo("com.example.MyViewClass2")
    ViewNode.readDrawChildren { drawChildren -> assertThat((node4.drawChildren()[0] as DrawViewImage).image).isEqualTo(image4) }
    assertThat(node4.children).isEmpty()
    assertThat((node4.transformedBounds as Polygon).xpoints).isEqualTo(intArrayOf(25, 75, 23, 78))
    assertThat((node4.transformedBounds as Polygon).ypoints).isEqualTo(intArrayOf(125, 127, 250, 253))

    val node5 = tree.children[2]
    assertThat(node5.drawId).isEqualTo(5)
    assertThat(node5.x).isEqualTo(0)
    assertThat(node5.y).isEqualTo(0)
    assertThat(node5.width).isEqualTo(300)
    assertThat(node5.height).isEqualTo(200)
    assertThat(node5.qualifiedName).isEqualTo("androidx.compose.ui.platform.ComposeView")
    ViewNode.readDrawChildren { drawChildren -> assertThat((node5.drawChildren()[0] as DrawViewImage).image).isEqualTo(image5) }
    assertThat(node5.children.map { it.drawId }).containsExactly(-2L, -5L)

    assertThat(loggedEvent).isEqualTo(DynamicLayoutInspectorEventType.INITIAL_RENDER)
  }

  private fun assertExpectedErrorIfSkiaRespondsWith(msg: String, skiaAnswer: () -> Any) {
    val banner = InspectorBanner(projectRule.project)

    val skiaParser: SkiaParser = mock()
    `when`(skiaParser.getViewTree(eq(sample.bytes), any(), any(), any())).thenAnswer { skiaAnswer() }

    val treeLoader = AppInspectionTreeLoader(
      projectRule.project,
      logEvent = { fail() }, // Metrics shouldn't be logged until we come back with a screenshot
      skiaParser
    )
    val (window, _) = treeLoader.loadComponentTree(createFakeData(), ResourceLookup(projectRule.project))!!
    window!!.refreshImages(1.0)

    assertThat(banner.text.text).isEqualTo(msg)
  }

  @Test
  fun testUnsupportedSkpVersion() {
    assertExpectedErrorIfSkiaRespondsWith("No renderer supporting SKP version 123 found. Rotation disabled.") {
      throw UnsupportedPictureVersionException(123)
    }
  }

  @Test
  fun testSkpParsingFailed() {
    assertExpectedErrorIfSkiaRespondsWith("Invalid picture data received from device. Rotation disabled.") {
      throw ParsingFailedException()
    }
  }

  @Test
  fun testInvalidSkp() {
    assertExpectedErrorIfSkiaRespondsWith("Invalid picture data received from device. Rotation disabled.") {
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
  fun testCanProcessBitmapScreenshots() {
    val skiaParser: SkiaParser = mock()
    `when`(skiaParser.getViewTree(any(), any(), any(), any())).thenThrow(AssertionError("SKIA not used in bitmap mode"))
    var loggedEvent: DynamicLayoutInspectorEventType? = null
    val treeLoader = AppInspectionTreeLoader(
      projectRule.project,
      // Initial event is only ever logged one time
      logEvent = { assertThat(loggedEvent).isNull(); loggedEvent = it },
      skiaParser
    )

    val data = createFakeData(ViewProtocol.Screenshot.Type.BITMAP)
    val (window, generation) = treeLoader.loadComponentTree(data, ResourceLookup(projectRule.project))!!
    assertThat(data.generation).isEqualTo(generation)
    window!!.refreshImages(1.0)

    val resultImage = ViewNode.readDrawChildren { drawChildren -> (window.root.drawChildren()[0] as DrawViewImage).image }
    ImageDiffUtil.assertImageSimilar("image1.png", sample.image, resultImage, 0.01)

    assertThat(loggedEvent).isEqualTo(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
  }
}

private fun ByteArray.compress(): ByteArray {
  val deflater = Deflater(Deflater.BEST_SPEED)
  deflater.setInput(this)
  deflater.finish()
  val buffer = ByteArray(1024 * 100)
  val baos = ByteArrayOutputStream()
  while (!deflater.finished()) {
    val count = deflater.deflate(buffer)
    if (count <= 0) break
    baos.write(buffer, 0, count)
  }
  baos.flush()
  return baos.toByteArray()
}

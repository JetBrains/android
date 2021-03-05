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
package com.android.tools.idea.layoutinspector.pipeline.transport

import com.android.io.readImage
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.layoutinspector.skia.SkiaParser
import com.android.tools.idea.layoutinspector.skia.UnsupportedPictureVersionException
import com.android.tools.idea.layoutinspector.model.AndroidWindow.ImageType
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.skia.ParsingFailedException
import com.android.tools.idea.layoutinspector.skia.SkiaParserImpl
import com.android.tools.idea.layoutinspector.skia.SkiaParserServerConnection
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.layoutinspector.LayoutInspectorUtils
import com.android.tools.layoutinspector.SkiaViewNode
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.internal.verification.Times
import java.awt.Image
import java.awt.Polygon
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class TransportTreeLoaderTest {

  private val eventStr = """
      tree {
        payload_id: 111
        payload_type: SKP
        root {
          draw_id: 1
          width: 100
          height: 200
          class_name: 2
          package_name: 3
          sub_view {
            draw_id: 2
            x: 10
            y: 10
            width: 50
            height: 100
            class_name: 4
            package_name: 3
            sub_view {
              draw_id: 3
              x: 20
              y: 20
              width: 20
              height: 50
              class_name: 2
              package_name: 3
            }
          }
          sub_view {
            draw_id: 4
            x: 30
            y: 120
            width: 40
            height: 50
            class_name: 4
            package_name: 3
            transformed_bounds {
              top_left_x: 25
              top_left_y: 125
              top_right_x: 75
              top_right_y: 127
              bottom_left_x: 23
              bottom_left_y: 250
              bottom_right_x: 78
              bottom_right_y: 253
            }
          }
          sub_view {
            draw_id: -2
            x: 30
            y: 170
            width: 70
            height: 30
            class_name: 5
            compose_filename: 6
            compose_package_hash: 12345678
            compose_offset: 300,
            compose_line_number: 12,
            transformed_bounds {
              top_left_x: 32
              top_left_y: 171
              top_right_x: 95
              top_right_y: 170
              bottom_left_x: 38
              bottom_left_y: 180
              bottom_right_x: 80
              bottom_right_y: 195
            }
          }
        }
        string {
          id: 1
          str: "en-us"
        }
        string {
          id: 2
          str: "MyViewClass1"
        }
        string {
          id: 3
          str: "com.example"
        }
        string {
          id: 4
          str: "MyViewClass2"
        }
        string {
          id: 5
          str: "Text"
        }
        string {
          id: 6
          str: "MySource.kt"
        }
        resources {
          api_level: 29
          configuration {
            country_code: 1
          }
        }
        all_window_ids: 123
        all_window_ids: 456
      }
    """.trimIndent()
  private val event = LayoutInspectorProto.LayoutInspectorEvent.newBuilder().also { TextFormat.getParser().merge(eventStr, it) }.build()

  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var sampleImage: BufferedImage
  private lateinit var sampleDeflatedBytes: ByteArray

  @Before
  fun setUp() {
    val origImage = getWorkspaceRoot().resolve("$TEST_DATA_PATH/image1.png").readImage()
    sampleImage = LayoutInspectorUtils.createImage565(ByteBuffer.allocate(origImage.width * origImage.height * 2), origImage.width,
                                                      origImage.height)
    val graphics = sampleImage.graphics
    graphics.drawImage(origImage, 0, 0, null)
    val deflater = Deflater(Deflater.BEST_SPEED)
    val dataElements = sampleImage.raster.getDataElements(0, 0, sampleImage.width, sampleImage.height,
                                                          ShortArray(sampleImage.width * sampleImage.height)) as ShortArray
    val imageBytes = ArrayList<Byte>(sampleImage.width * sampleImage.height * 2)
    dataElements.flatMapTo(imageBytes) { listOf((it.toInt() and 0xFF).toByte(), (it.toInt() ushr 8).toByte()) }
    deflater.setInput(imageBytes.toByteArray())
    deflater.finish()
    val buffer = ByteArray(1024 * 100)
    val baos = ByteArrayOutputStream()
    while (!deflater.finished()) {
      val count = deflater.deflate(buffer)
      if (count <= 0) {
        break
      }
      baos.write(buffer, 0, count)
    }
    baos.flush()
    sampleDeflatedBytes = baos.toByteArray()
  }
  /**
   * Creates a mock [TransportInspectorClient] with tree loader initialized.
   *
   * Callers can continue to mock the returned client if necessary.
   */
  private fun createMockTransportClient(): TransportInspectorClient {
    val client: TransportInspectorClient = mock()
    `when`(client.treeLoader).thenReturn(TransportTreeLoader(projectRule.project, client, mock()))
    return client
  }

  @Test
  fun testLoad() {
    val image1: Image = mock()
    val image2: Image = mock()
    val image3: Image = mock()
    val image4: Image = mock()

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
      ))
    ))

    val client = createMockTransportClient()
    val payload = "samplepicture".toByteArray()
    `when`(client.getPayload(111)).thenReturn(payload)
    val skiaParser: SkiaParser = mock()
    `when`(skiaParser.getViewTree(eq(payload), argThat { req -> req.map { it.id }.sorted() == listOf(-2L, 1L, 2L, 3L, 4L) }, any(), any()))
      .thenReturn(skiaResponse)

    val (window, _) = client.treeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), skiaParser)!!
    window!!.refreshImages(1.0)
    val tree = window.root
    assertThat(tree.drawId).isEqualTo(1)
    assertThat(tree.x).isEqualTo(0)
    assertThat(tree.y).isEqualTo(0)
    assertThat(tree.width).isEqualTo(100)
    assertThat(tree.height).isEqualTo(200)
    assertThat(tree.qualifiedName).isEqualTo("com.example.MyViewClass1")
    ViewNode.readDrawChildren { drawChildren -> assertThat ((tree.drawChildren()[0] as DrawViewImage).image).isEqualTo(image1) }
    assertThat(tree.children.map { it.drawId }).containsExactly(2L, 4L, -2L).inOrder()

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
    assertThat((node4.transformedBounds as Polygon).xpoints).isEqualTo(intArrayOf(25, 75, 78, 23))
    assertThat((node4.transformedBounds as Polygon).ypoints).isEqualTo(intArrayOf(125, 127, 253, 250))

    val node5 = tree.children[2]
    assertThat(node5.drawId).isEqualTo(-2)
    assertThat(node5.x).isEqualTo(30)
    assertThat(node5.y).isEqualTo(170)
    assertThat(node5.width).isEqualTo(70)
    assertThat(node5.height).isEqualTo(30)
    assertThat(node5.qualifiedName).isEqualTo("Text")
    ViewNode.readDrawChildren { drawChildren -> assertThat(node5.drawChildren()).isEmpty() }
    assertThat(node5.children).isEmpty()
    assertThat((node5.transformedBounds as Polygon).xpoints).isEqualTo(intArrayOf(32, 95, 80, 38))
    assertThat((node5.transformedBounds as Polygon).ypoints).isEqualTo(intArrayOf(171, 170, 195, 180))
  }

  @Test
  fun testFallback() {
    val event = LayoutInspectorProto.LayoutInspectorEvent.newBuilder(event).apply {
      tree = LayoutInspectorProto.ComponentTreeEvent.newBuilder(tree).apply {
        payloadType = PayloadType.BITMAP_AS_REQUESTED
        root = LayoutInspectorProto.View.newBuilder(root).apply {
          width = sampleImage.width
          height = sampleImage.height
        }.build()
      }.build()
    }.build()

    val client = createMockTransportClient()
    `when`(client.getPayload(111)).thenReturn(sampleDeflatedBytes)

    val (window, _) = client.treeLoader.loadComponentTree(event, ResourceLookup(projectRule.project))!!
    window!!.refreshImages(1.0)
    val tree = window.root

    assertThat(window.imageType).isEqualTo(ImageType.BITMAP_AS_REQUESTED)
    ViewNode.readDrawChildren { drawChildren ->
      ImageDiffUtil.assertImageSimilar("image1.png", sampleImage, (tree.drawChildren()[0] as DrawViewImage).image as BufferedImage, 0.0)
      assertThat(tree.flatten().flatMap { it.drawChildren().asSequence() }.count { it is DrawViewImage }).isEqualTo(1)
    }
    verify(client).logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
  }

  @Test
  fun testUnsupportedSkpVersion() {
    val banner = InspectorBanner(projectRule.project)
    val client = createMockTransportClient()
    val payload = "samplepicture".toByteArray()
    `when`(client.getPayload(111)).thenReturn(payload)

    val connection: SkiaParserServerConnection = mock()
    `when`(connection.getViewTree(eq(payload), any(), any())).thenAnswer { throw UnsupportedPictureVersionException(123) }
    var calledBack = false
    val skiaParser = SkiaParserImpl({ calledBack = true }, { connection })

    val (window, _) = client.treeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), skiaParser)!!
    window!!.refreshImages(1.0)
    assertThat(calledBack).isTrue()
    assertThat(banner.text.text).isEqualTo("No renderer supporting SKP version 123 found. Rotation disabled.")
    // Metrics shouldn't be logged until we come back with a screenshot
    verify(client, Times(0)).logEvent(any(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType::class.java))
  }

  @Test
  fun testInvalidSkp() {
    val banner = InspectorBanner(projectRule.project)
    val client = createMockTransportClient()
    val payload = "samplepicture".toByteArray()
    `when`(client.getPayload(111)).thenReturn(payload)

    val connection: SkiaParserServerConnection = mock()
    `when`(connection.getViewTree(eq(payload), any(), any())).thenThrow(ParsingFailedException::class.java)
    var calledBack = false
    val skiaParser = SkiaParserImpl({ calledBack = true }, { connection })

    val (window, _) = client.treeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), skiaParser)!!
    window!!.refreshImages(1.0)
    assertThat(calledBack).isTrue()
    assertThat(banner.text.text).isEqualTo("Invalid picture data received from device. Rotation disabled.")
    // Metrics shouldn't be logged until we come back with a screenshot
    verify(client, Times(0)).logEvent(any(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType::class.java))
  }

  @Test
  fun testOtherProblem() {
    val banner = InspectorBanner(projectRule.project)
    val client = createMockTransportClient()
    val payload = "samplepicture".toByteArray()
    `when`(client.getPayload(111)).thenReturn(payload)

    val connection: SkiaParserServerConnection = mock()
    `when`(connection.getViewTree(eq(payload), any(), any())).thenThrow(RuntimeException::class.java)

    var calledBack = false
    val skiaParser = SkiaParserImpl({ calledBack = true }, { connection })

    val (window, _) = client.treeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), skiaParser)!!
    window!!.refreshImages(1.0)
    assertThat(calledBack).isTrue()
    assertThat(banner.text.text).isEqualTo("Problem launching renderer. Rotation disabled.")
    // Metrics shouldn't be logged until we come back with a screenshot
    verify(client, Times(0)).logEvent(any(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType::class.java))
  }

  @Test
  fun testEmptyTree() {
    val emptyTreeEvent = LayoutInspectorProto.LayoutInspectorEvent.newBuilder().apply {
      tree = LayoutInspectorProto.ComponentTreeEvent.newBuilder(tree).apply {
        payloadType = PayloadType.NONE
        generation = 17
      }.build()
    }.build()

    val client = createMockTransportClient()
    val skiaParser: SkiaParser = mock()
    val (window, generation) = client.treeLoader.loadComponentTree(emptyTreeEvent, ResourceLookup(projectRule.project), skiaParser)!!
    assertThat(window).isNull()
    assertThat(generation).isEqualTo(17)
  }

  @Test
  fun testBitmap() {
    val client = createMockTransportClient()
    `when`(client.getPayload(1111)).thenReturn(sampleDeflatedBytes)
    val event = LayoutInspectorProto.LayoutInspectorEvent.newBuilder().apply {
      tree = LayoutInspectorProto.ComponentTreeEvent.newBuilder().apply {
        root = LayoutInspectorProto.View.newBuilder().apply {
          drawId = 1
          width = sampleImage.width
          height = sampleImage.height
        }.build()
        payloadId = 1111
        payloadType = PayloadType.BITMAP_AS_REQUESTED
      }.build()
    }.build()
    val (window, _) = client.treeLoader.loadComponentTree(event, ResourceLookup(projectRule.project))!!
    window!!.refreshImages(1.0)
    val resultImage = ViewNode.readDrawChildren { drawChildren -> (window.root.drawChildren()[0] as DrawViewImage).image }
    ImageDiffUtil.assertImageSimilar("image1.png", sampleImage, resultImage, 0.01)
  }
}

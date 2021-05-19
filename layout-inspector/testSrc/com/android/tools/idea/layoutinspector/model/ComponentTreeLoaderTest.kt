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
package com.android.tools.idea.layoutinspector.model

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.layoutinspector.SkiaParserService
import com.android.tools.idea.layoutinspector.UnsupportedPictureVersionException
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBanner
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType.PNG_AS_REQUESTED
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.internal.verification.Times
import java.awt.Image
import java.awt.Polygon
import java.awt.image.BufferedImage
import java.io.File

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

class ComponentTreeLoaderTest {

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

    val client: DefaultInspectorClient = mock()
    val payload = "samplepicture".toByteArray()
    `when`(client.getPayload(111)).thenReturn(payload)
    val skiaParser: SkiaParserService = mock()
    `when`(skiaParser.getViewTree(eq(payload), argThat { req -> req.map { it.drawId }.sorted() == listOf(1L, 2L, 3L, 4L) }, any(), any()))
      .thenReturn(skiaResponse)

    val (window, _) =
      ComponentTreeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), client, skiaParser, projectRule.project)!!
    window!!.refreshImages(1.0)
    val tree = window.root
    assertThat(tree.drawId).isEqualTo(1)
    assertThat(tree.x).isEqualTo(0)
    assertThat(tree.y).isEqualTo(0)
    assertThat(tree.width).isEqualTo(100)
    assertThat(tree.height).isEqualTo(200)
    assertThat(tree.qualifiedName).isEqualTo("com.example.MyViewClass1")
    ViewNode.readDrawChildren { drawChildren -> assertThat ((tree.drawChildren()[0] as DrawViewImage).image).isEqualTo(image1) }
    assertThat(tree.children.map { it.drawId }).containsExactly(2L, 4L).inOrder()

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
  }

  @Test
  fun testFallback() {
    val imageFile = File(TestUtils.getWorkspaceRoot(), "$TEST_DATA_PATH/image1.png")
    val imageBytes = imageFile.readBytes()
    val event = LayoutInspectorProto.LayoutInspectorEvent.newBuilder(event).apply {
      tree = LayoutInspectorProto.ComponentTreeEvent.newBuilder(tree).apply {
        payloadType = PNG_AS_REQUESTED
      }.build()
    }.build()

    val client: DefaultInspectorClient = mock()
    `when`(client.getPayload(111)).thenReturn(imageBytes)

    val (window, _) = ComponentTreeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), client, projectRule.project)!!
    window!!.refreshImages(1.0)
    val tree = window.root

    assertThat(window.imageType).isEqualTo(PNG_AS_REQUESTED)
    ViewNode.readDrawChildren { drawChildren ->
      ImageDiffUtil.assertImageSimilar(imageFile, (tree.drawChildren()[0] as DrawViewImage).image as BufferedImage, 0.0)
      assertThat(tree.flatten().flatMap { it.drawChildren().asSequence() }.count { it is DrawViewImage }).isEqualTo(1)
    }
    verify(client).logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
  }

  @Test
  fun testUnsupportedSkpVersion() {
    val banner = InspectorBanner(projectRule.project)
    val client: DefaultInspectorClient = mock()
    val payload = "samplepicture".toByteArray()
    `when`(client.getPayload(111)).thenReturn(payload)

    val skiaParser: SkiaParserService = mock()
    `when`(skiaParser.getViewTree(eq(payload), any(), any(), any())).thenAnswer { throw UnsupportedPictureVersionException(123) }

    val (window, _) =
      ComponentTreeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), client, skiaParser, projectRule.project)!!
    window!!.refreshImages(1.0)
    verify(client).requestScreenshotMode()
    assertThat(banner.text.text).isEqualTo("No renderer supporting SKP version 123 found. Rotation disabled.")
    // Metrics shouldn't be logged until we come back with a screenshot
    verify(client, Times(0)).logEvent(any(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType::class.java))
  }

  @Test
  fun testInvalidSkp() {
    val banner = InspectorBanner(projectRule.project)
    val client: DefaultInspectorClient = mock()
    val payload = "samplepicture".toByteArray()
    `when`(client.getPayload(111)).thenReturn(payload)

    val skiaParser: SkiaParserService = mock()
    `when`(skiaParser.getViewTree(eq(payload), any(), any(), any())).thenReturn(null)

    val (window, _) =
      ComponentTreeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), client, skiaParser, projectRule.project)!!
    window!!.refreshImages(1.0)
    verify(client).requestScreenshotMode()
    assertThat(banner.text.text).isEqualTo("Invalid picture data received from device. Rotation disabled.")
    // Metrics shouldn't be logged until we come back with a screenshot
    verify(client, Times(0)).logEvent(any(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType::class.java))
  }

  @Test
  fun testOtherProblem() {
    val banner = InspectorBanner(projectRule.project)
    val client: DefaultInspectorClient = mock()
    val payload = "samplepicture".toByteArray()
    `when`(client.getPayload(111)).thenReturn(payload)

    val skiaParser: SkiaParserService = mock()
    `when`(skiaParser.getViewTree(eq(payload), any(), any(), any())).thenAnswer { throw Exception() }

    val (window, _) =
      ComponentTreeLoader.loadComponentTree(event, ResourceLookup(projectRule.project), client, skiaParser, projectRule.project)!!
    window!!.refreshImages(1.0)
    verify(client).requestScreenshotMode()
    assertThat(banner.text.text).isEqualTo("Problem launching renderer. Rotation disabled.")
    // Metrics shouldn't be logged until we come back with a screenshot
    verify(client, Times(0)).logEvent(any(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType::class.java))
  }

  @Test
  fun testEmptyTree() {
    val eventTreeEvent = LayoutInspectorProto.LayoutInspectorEvent.newBuilder().apply {
      tree = LayoutInspectorProto.ComponentTreeEvent.newBuilder(tree).apply {
        payloadType = PayloadType.NONE
        generation = 17
      }.build()
    }.build()

    val client: DefaultInspectorClient = mock()
    val skiaParser: SkiaParserService = mock()
    val (window, generation) =
      ComponentTreeLoader.loadComponentTree(eventTreeEvent, ResourceLookup(projectRule.project), client, skiaParser, projectRule.project)!!
    assertThat(window).isNull()
    assertThat(generation).isEqualTo(17)
  }
}

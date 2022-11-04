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
package com.android.tools.idea.layoutinspector.skia

import com.android.flags.junit.SetFlagRule
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils
import com.android.tools.idea.FakeSdkRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.proto.SkiaParser.InspectorView
import com.android.tools.idea.layoutinspector.proto.SkiaParser.RequestedNodeInfo
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.InvalidPictureException
import com.android.tools.layoutinspector.LayoutInspectorUtils
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

// TODO(152816022): testBuildTree
class SkiaParserTest {

  @Test
  fun testInvalidSkp() {
    try {
      SkiaParserImpl({}).getViewTree("foobarbaz".toByteArray(), emptyList(), 1.0)
      fail()
    }
    catch (expected: InvalidPictureException) {}
  }

  /**
   * This is what we get when a view draw before and after it's children: the root, first, and last child all have the same id.
   */
  @Test
  fun testOverUnder() {
    val eventStr = """
        id: 1
        children {
          id: 1
          image: "
          """.trim() + (1..200).joinToString(separator = "") { "\\%1\$03o\\%1\$03o\\000\\777".format(it) } + """ "
        }
        children {
          id: 4
          image: "
          """.trim() + (100..135).joinToString(separator = "") { "\\000\\%1\$03o\\%1\$03o\\777".format(it) } + """ "
        }
        children {
          id: 1
          image: "
          """.trim() + (1..200).joinToString(separator = "") { "\\%1\$03o\\000\\%1\$03o\\777".format(it) } + """ "
        }""".trim()
    val tree = InspectorView.newBuilder().also {
      TextFormat.getParser().merge(eventStr, it)
    }.build()

    val requestedNodes = mapOf(
      1L to LayoutInspectorUtils.makeRequestedNodeInfo(1, 0, 0, 10, 20)!!,
      4L to LayoutInspectorUtils.makeRequestedNodeInfo(4, 3, 12, 4, 5)!!
    )
    val root = LayoutInspectorUtils.buildTree(tree, mapOf(), { false }, requestedNodes)!!

    assertThat(root.id).isEqualTo(1)
    assertThat(root.image).isNull()

    val child1 = root.children[0]
    assertThat(child1.id).isEqualTo(1)
    ImageDiffUtil.assertImageSimilar(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/buildTreeImg1.png"), child1.image as BufferedImage, 0.0)

    val child2 = root.children[1]
    assertThat(child2.id).isEqualTo(4)
    ImageDiffUtil.assertImageSimilar(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/buildTreeImg2.png"), child2.image as BufferedImage, 0.0)

    val child3 = root.children[2]
    assertThat(child3.id).isEqualTo(1)
    ImageDiffUtil.assertImageSimilar(TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/buildTreeImg3.png"), child3.image as BufferedImage, 0.0)
  }
}

class SkiaParserWithSdkTest {
  val projectRule = AndroidProjectRule.inMemory()
  private val fakeSdkRule = FakeSdkRule(projectRule).withLocalPackage("skiaparser;1", "skiaparser/1")

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fakeSdkRule)!!

  @Test
  fun testUnsupportedVersion() {
    var called = false
    try {
      SkiaParserImpl({ called = true }).getViewTree("skiapict".toByteArray().plus(byteArrayOf(127, 1, 2, 3, 4, 5)), emptyList(), 1.0)
      fail()
    }
    catch (expected: UnsupportedPictureVersionException) {}
    assertThat(called).isTrue()
  }
}

class SkiaParserIntegrationTest {
  @get:Rule
  val flagRule = SetFlagRule(StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER, true)

  @Test
  fun testRunServer() {
    val server = SkiaParserServerConnection(mock())
    // Call createGrpcClient directly to skip running the server binary
    val port = server.createGrpcClient()
    val serverThread = Thread { runServer(port) }
    serverThread.start()
    val node1 = RequestedNodeInfo.newBuilder().apply {
      id = 1
      width = 1000
      height = 2000
      x = 0
      y = 0
    }.build()
    val node2 = RequestedNodeInfo.newBuilder().apply {
      id = 2
      width = 500
      height = 1000
      x = 100
      y = 100
    }.build()
    val node4 = RequestedNodeInfo.newBuilder().apply {
      id = 4
      width = 400
      height = 500
      x = 300
      y = 1200
    }.build()
    val (root, imageMap) = server.getViewTree(generateBoxes(), listOf(node1, node2, node4), 1.0)
    assertThat(imageMap.values.map { it.size() }).containsExactly(8000000, 2000000, 800000)
    val expected = Node(1, Node(1), Node(2, Node(2)), Node(4, Node(4)))
    assertIdsEqual(expected, root)
    assertImagesCorrect(root, imageMap)
    server.shutdown()
    serverThread.join()
  }

  /**
   * Test that shutdown() and getViewTree() can be called on different threads without interfering with each other.
   */
  @Test
  fun testThreadSafety() {
    val iterations = 100
    var hasErrors = false
    val parser = SkiaParserImpl({ hasErrors = true }) {
      val server = SkiaParserServerConnection(mock())
      // Call createGrpcClient directly to skip running the server binary
      val port = server.createGrpcClient()
      val serverThread = Thread { runServer(port) }
      serverThread.start()
      server
    }
    var numberOfViewTreeCalls = 0
    val getViewTreeThread = Thread {
      for (i in 1..iterations) {
        parser.getViewTree(generateBoxes(), emptyList(), 1.0)
        numberOfViewTreeCalls++

        // Give the shutdownThread a chance to execute:
        Thread.yield()
      }
    }
    val shutdownThread = Thread {
      while (!hasErrors && numberOfViewTreeCalls < iterations) {
        // Starting a server is so slow that all the shutdown calls could finish without a single connection being created.
        // Increase the upper limit such that we
        parser.shutdown()

        // Give the getViewTreeThread a chance to execute:
        Thread.yield()
      }
    }
    getViewTreeThread.start()
    shutdownThread.start()
    getViewTreeThread.join()
    shutdownThread.join()

    parser.shutdown()
    if (hasErrors) {
      error("Errors encountered")
    }
  }

  private fun assertImagesCorrect(root: InspectorView, imageMap: Map<Int, ByteString>) {
    val remainingImages = imageMap.toMutableMap()
    assertImagesCorrectInternal(root, remainingImages)
    assertThat(remainingImages).isEmpty()
  }

  private fun assertImagesCorrectInternal(node: InspectorView, remainingImages: MutableMap<Int, ByteString>) {
    if (node.imageId != 0) {
      assertTrue(node.image?.isEmpty != false)
      assertTrue(remainingImages.remove(node.imageId) != null)
    }
    node.childrenList.forEach { assertImagesCorrectInternal(it, remainingImages) }
  }

  private fun assertIdsEqual(expected: Node, root: InspectorView) {
    assertThat(root.id).isEqualTo(expected.id)
    assertThat(root.childrenCount).isEqualTo(expected.children.size)
    root.childrenList.zip(expected.children).forEach { (actual, expected) -> assertIdsEqual(expected, actual) }
  }

  private class Node(val id: Int, vararg val children: Node)

  companion object {
    init {
      System.loadLibrary("skiaparser-test")
    }
    @JvmStatic private external fun generateBoxes(): ByteArray
    @JvmStatic private external fun runServer(port: Int)
  }
}


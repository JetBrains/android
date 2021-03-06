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
package com.android.tools.idea.layoutinspector

import com.android.repository.testframework.MockFileOp
import com.android.testutils.ImageDiffUtil
import com.android.testutils.MockitoKt
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.FakeSdkRule
import com.android.tools.idea.layoutinspector.proto.SkiaParser.RequestedNodeInfo
import com.android.tools.idea.layoutinspector.proto.SkiaParser.InspectorView
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.LayoutInspectorUtils
import com.google.common.truth.Truth.assertThat
import io.grpc.netty.NettyChannelBuilder
import junit.framework.TestCase.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData"

// TODO(152816022): testBuildTree
class SkiaParserTest {

  @Test
  fun testGetSkpVersion() {
    val version = SkiaParser.getSkpVersion("skiapict".toByteArray().plus(byteArrayOf(10, 0, 1, 0)).plus("blah".toByteArray()))
    assertEquals(65546, version)
  }

  @Test
  fun testInvalidSkp() {
    try {
      SkiaParser.getViewTree("foobarbaz".toByteArray(), emptyList(), 1.0)
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
    val tree = com.android.tools.idea.layoutinspector.proto.SkiaParser.InspectorView.newBuilder().also {
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
    ImageDiffUtil.assertImageSimilar(getWorkspaceRoot().resolve("$TEST_DATA_PATH/buildTreeImg1.png"), child1.image as BufferedImage, 0.0)

    val child2 = root.children[1]
    assertThat(child2.id).isEqualTo(4)
    ImageDiffUtil.assertImageSimilar(getWorkspaceRoot().resolve("$TEST_DATA_PATH/buildTreeImg2.png"), child2.image as BufferedImage, 0.0)

    val child3 = root.children[2]
    assertThat(child3.id).isEqualTo(1)
    ImageDiffUtil.assertImageSimilar(getWorkspaceRoot().resolve("$TEST_DATA_PATH/buildTreeImg3.png"), child3.image as BufferedImage, 0.0)
  }
}

// TODO: test with downloading (currently no way to mock out installation)
class SkiaParserTest2 {
  val projectRule = AndroidProjectRule.inMemory()
  private val fakeSdkRule = FakeSdkRule(projectRule)
    .withLocalPackage("skiaparser;1")

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fakeSdkRule)!!

  @Test
  fun testFindServerInfoForSkpVersion() {
    val fileOp = fakeSdkRule.fileOp as MockFileOp
    fileOp.recordExistingFile(File(fakeSdkRule.sdkPath, "skiaparser/1/version-map.xml").path, """
      <?xml version="1.0" encoding="utf-8"?>
      <versionMapping>
        <server version="1" skpStart="1" skpEnd="10"/>
        <server version="2" skpStart="11" skpEnd="15"/>
        <server version="3" skpStart="16" skpEnd="20"/>
      </versionMapping>
    """.trimIndent())

    assertThat(SkiaParser.findServerInfoForSkpVersion(13)!!.serverVersion).isEqualTo(2)
    assertThat(SkiaParser.findServerInfoForSkpVersion(25)).isNull()

    fakeSdkRule.addLocalPackage("skiaparser;2")
    fileOp.recordExistingFile(File(fakeSdkRule.sdkPath, "skiaparser/2/version-map.xml").path, """
      <?xml version="1.0" encoding="utf-8"?>
      <versionMapping>
        <server version="1" skpStart="1" skpEnd="10"/>
        <server version="2" skpStart="11" skpEnd="15"/>
        <server version="3" skpStart="16" skpEnd="20"/>
        <server version="4" skpStart="21" skpEnd="25"/>
      </versionMapping>
    """.trimIndent())

    assertThat(SkiaParser.findServerInfoForSkpVersion(25)!!.serverVersion).isEqualTo(4)
  }
}

class SkiaParserIntegrationTest {
  @Test
  fun testRunServer() {
    val serverInfo = ServerInfo(null, 0, 0)
    val port = serverInfo.createGrpcClient()
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
    val (root, imageMap) = serverInfo.getViewTree(generateBoxes(), listOf(node1, node2, node4), 1.0) ?: error("no result from server")
    assertThat(imageMap.values.map { it.size() }).containsExactly(8000000, 2000000, 800000)
    val expected = Node(1, Node(1), Node(2, Node(2)), Node(4, Node(4)))
    assertIdsEqual(expected, root)
    assertImagesCorrect(root, imageMap)
    serverInfo.shutdown()
    serverThread.join()
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


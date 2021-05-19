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
import com.android.testutils.TestUtils
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.idea.FakeSdkRule
import com.android.tools.idea.protobuf.TextFormat
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.image.BufferedImage
import java.io.File

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
      1L to RequestedNodeInfo(1, 10, 20, 0, 0),
      4L to RequestedNodeInfo(4, 4, 5, 3, 12)
    )
    val root = SkiaParser.buildTree(tree, { false }, requestedNodes)!!

    assertThat(root.id).isEqualTo(1)
    assertThat(root.image).isNull()

    val child1 = root.children[0]
    assertThat(child1.id).isEqualTo(1)
    ImageDiffUtil.assertImageSimilar(
      File(TestUtils.getWorkspaceRoot(), "$TEST_DATA_PATH/buildTreeImg1.png"),
      child1.image as BufferedImage, 0.0)

    val child2 = root.children[1]
    assertThat(child2.id).isEqualTo(4)
    ImageDiffUtil.assertImageSimilar(
      File(TestUtils.getWorkspaceRoot(), "$TEST_DATA_PATH/buildTreeImg2.png"),
      child2.image as BufferedImage, 0.0)

    val child3 = root.children[2]
    assertThat(child3.id).isEqualTo(1)
    ImageDiffUtil.assertImageSimilar(
      File(TestUtils.getWorkspaceRoot(), "$TEST_DATA_PATH/buildTreeImg3.png"),
      child3.image as BufferedImage, 0.0)
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
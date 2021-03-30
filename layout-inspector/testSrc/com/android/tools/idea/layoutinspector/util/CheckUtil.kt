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
package com.android.tools.idea.layoutinspector.util

import com.android.testutils.ImageDiffUtil
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.DrawViewNode
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.awt.image.BufferedImage

/**
 * Various checks for tests.
 */
object CheckUtil {

  /**
   * Return the line at the [offset] of the specified [file] as a string.
   */
  fun findLineAtOffset(file: VirtualFile, offset: Int): String {
    val text = String(file.contentsToByteArray(), Charsets.UTF_8).replace("\r\n", "\n")
    val line = StringUtil.offsetToLineColumn(text, offset)
    val lineText = text.substring(offset - line.column, text.indexOf('\n', offset))
    return lineText.trim()
  }

  /**
   * Check whether the draw tree of [actual] is the same as that of [expected].
   * Right now the check is pretty cursory, but it can be expanded as needed.
   */
  fun assertDrawTreesEqual(expected: ViewNode, actual: ViewNode) {
    assertEquals(expected.drawId, actual.drawId)
    ViewNode.readDrawChildren { drawChildren ->
      assertEquals("for node ${expected.drawId}", expected.drawChildren().size, actual.drawChildren().size)
      expected.drawChildren().zip(actual.drawChildren()).forEach { (expected, actual) -> checkTreesEqual(expected, actual) }
    }
  }

  private fun checkTreesEqual(expected: DrawViewNode, actual: DrawViewNode) {
    if (expected is DrawViewChild && actual is DrawViewChild) {
      assertDrawTreesEqual(expected.owner!!, actual.owner!!)
    }
    else if (expected is DrawViewImage && actual is DrawViewImage) {
      if (expected.image !is BufferedImage) {
        fail("expected image should be a BufferedImage for id ${expected.owner!!.drawId}")
      }
      ImageDiffUtil.assertImageSimilar("image", expected.image as BufferedImage, actual.image, 0.0)
    }
    else {
      fail("$actual was expected to be a ${expected.javaClass.name}")
    }
  }
}

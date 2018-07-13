/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.*
import org.jetbrains.android.AndroidTestBase
import org.junit.Test
import java.lang.Math.abs
import kotlin.test.assertEquals

class ParserTest {
  private val errorMargin = 0.0000001

  @Test
  fun checkParsedPageData() {
    val page: SketchPage = SketchParser.open(AndroidTestBase.getTestDataPath() + "/sketch/" + "123.json") ?: return

    assertEquals(page.objectId, "4A20F10B-61D2-4A1B-8BF1-623ACF2E7637")

    assertEquals(page.booleanOperation, -1)

    assertEquals(page.frame.x, 0)
    assertEquals(page.frame.y, 0)
    assertEquals(page.frame.height, 0)
    assertEquals(page.frame.width, 0)

    assertEquals(page.isFlippedHorizontal, false)

    assertEquals(page.isFlippedVertical, false)

    assertEquals(page.isVisible, true)

    assertEquals(page.name, "Page 1")

    assertEquals(page.rotation, 0)

    assertEquals(page.shouldBreakMaskChain(), false)

    assertEquals(page.style.miterLimit, 10)
    assertEquals(page.style.windingRule, 1)
  }

  @Test
fun checkParsedPosition() {
    val position = SketchParser.getPosition("{0.5, 0.67135115527602085}")

    assert(abs(position.first - 0.5) < errorMargin)
    assert(abs(position.second - 0.67135115527602085) < errorMargin)
  }
}
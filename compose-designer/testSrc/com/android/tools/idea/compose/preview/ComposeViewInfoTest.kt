/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ComposeViewInfo.serializeHits(x: Int, y: Int): String =
  findHitWithDepth(x, y)
    .sortedBy { it.first }
    .joinToString("\n") { "${it.first}: ${it.second.sourceLocation.fileName}" }

class ComposeViewInfoTest {
  private data class TestSourceLocation(
    override val fileName: String = "",
    override val lineNumber: Int = -1,
    override val packageHash: Int = -1,
  ) : SourceLocation

  @Test
  fun checkBoundHits() {
    val root =
      ComposeViewInfo(
        TestSourceLocation("root"),
        PxBounds(0, 0, 1000, 300),
        children =
          listOf(
            ComposeViewInfo(
              TestSourceLocation("child1"),
              PxBounds(0, 0, 0, 0),
              children = listOf(),
            ),
            ComposeViewInfo(
              TestSourceLocation("child2"),
              PxBounds(100, 100, 500, 300),
              children =
                listOf(
                  ComposeViewInfo(
                    TestSourceLocation("child2.2"),
                    PxBounds(250, 250, 500, 300),
                    children = listOf(),
                  )
                ),
            ),
            ComposeViewInfo(
              TestSourceLocation("child3"),
              PxBounds(400, 200, 1000, 300),
              children = listOf(),
            ),
          ),
      )

    assertTrue(
      "2000, 2000 should not hit any components",
      root.findHitWithDepth(2000, 2000).isEmpty(),
    )
    assertEquals("0: root".trimMargin(), root.serializeHits(0, 0))
    assertEquals(
      """0: root
                   |1: child2"""
        .trimMargin(),
      root.serializeHits(125, 125),
    )
    assertEquals("child2", root.findDeepestHits(125, 125).single().sourceLocation.fileName)
    assertEquals(
      """0: root
                   |1: child2
                   |2: child2.2"""
        .trimMargin(),
      root.serializeHits(260, 260),
    )
    assertEquals("child2.2", root.findDeepestHits(260, 260).single().sourceLocation.fileName)
    assertEquals(
      """0: root
                   |1: child2
                   |1: child3
                   |2: child2.2"""
        .trimMargin(),
      root.serializeHits(450, 260),
    )
    assertEquals("child2.2", root.findDeepestHits(450, 260).single().sourceLocation.fileName)
  }

  @Test
  fun checkLeafHits() {
    //              root
    //            /     \
    //          fileA    fileC
    //           /
    //        fileB (line 4)
    //       /              \
    //     fileA (line 5)    fileB  (line 7)
    //                             \
    //                             fileA (line 8)
    //
    // Given that all the components shown above contain the point x, y and the file A is passed
    // into the function findLeafHitsInFile will return both components on line 5 and line 8 of
    // file A.

    val root =
      ComposeViewInfo(
        TestSourceLocation("root"),
        PxBounds(0, 0, 1000, 300),
        children =
          listOf(
            ComposeViewInfo(TestSourceLocation("fileA"), PxBounds(0, 0, 0, 0), children = listOf()),
            ComposeViewInfo(
              TestSourceLocation("fileB", lineNumber = 4),
              PxBounds(0, 0, 200, 200),
              children =
                listOf(
                  ComposeViewInfo(
                    TestSourceLocation("fileA", lineNumber = 5),
                    PxBounds(0, 0, 200, 200),
                    children = listOf(),
                  ),
                  ComposeViewInfo(
                    TestSourceLocation("fileB", lineNumber = 7),
                    PxBounds(0, 0, 200, 200),
                    children =
                      listOf(
                        ComposeViewInfo(
                          TestSourceLocation("fileA", lineNumber = 8),
                          PxBounds(0, 0, 200, 200),
                          children = listOf(),
                        )
                      ),
                  ),
                ),
            ),
            ComposeViewInfo(
              TestSourceLocation("fileC", lineNumber = 10),
              PxBounds(400, 200, 1000, 300),
              children = listOf(),
            ),
          ),
      )

    assertTrue(root.findLeafHitsInFile(2000, 2000, "fileA").isEmpty())

    val leafHits = root.findLeafHitsInFile(125, 125, "fileA")
    assertEquals(leafHits.size, 2)
    assertEquals(leafHits.first().sourceLocation.fileName, "fileA")
    assertEquals(leafHits.first().sourceLocation.lineNumber, 8)
    assertEquals(leafHits[1].sourceLocation.fileName, "fileA")
    assertEquals(leafHits[1].sourceLocation.lineNumber, 5)
  }

  @Test
  fun checkAllHits() {
    //                      root
    //                  /          \
    //          fileA (line 1)      fileC
    //           /
    //       fileB (line 4)
    //       /              \
    //     fileA (line 5)    fileB  (line 7)
    //                             \
    //                             fileA (line 8)
    //
    // Given that file A is passed the function findAllHitsInFile will return all components aka the
    // ones on line 1 line 5 and line 8 of file A.

    val root =
      ComposeViewInfo(
        TestSourceLocation("root"),
        PxBounds(0, 0, 1000, 300),
        children =
          listOf(
            ComposeViewInfo(
              TestSourceLocation("fileA", lineNumber = 1),
              PxBounds(0, 0, 0, 0),
              children = listOf(),
            ),
            ComposeViewInfo(
              TestSourceLocation("fileB", lineNumber = 4),
              PxBounds(0, 0, 200, 200),
              children =
                listOf(
                  ComposeViewInfo(
                    TestSourceLocation("fileA", lineNumber = 5),
                    PxBounds(0, 0, 200, 200),
                    children = listOf(),
                  ),
                  ComposeViewInfo(
                    TestSourceLocation("fileB", lineNumber = 7),
                    PxBounds(0, 0, 200, 200),
                    children =
                      listOf(
                        ComposeViewInfo(
                          TestSourceLocation("fileA", lineNumber = 8),
                          PxBounds(0, 0, 200, 200),
                          children = listOf(),
                        )
                      ),
                  ),
                ),
            ),
            ComposeViewInfo(
              TestSourceLocation("fileC", lineNumber = 10),
              PxBounds(400, 200, 1000, 300),
              children = listOf(),
            ),
          ),
      )

    val leafHits = root.findAllHitsInFile("fileA")
    assertEquals(leafHits.size, 3)
    assertEquals(leafHits.first().sourceLocation.fileName, "fileA")
    assertEquals(leafHits.first().sourceLocation.lineNumber, 8)
    assertEquals(leafHits[1].sourceLocation.fileName, "fileA")
    assertEquals(leafHits[1].sourceLocation.lineNumber, 5)
    assertEquals(leafHits[2].sourceLocation.fileName, "fileA")
    assertEquals(leafHits[2].sourceLocation.lineNumber, 1)
  }

  @Test
  fun checkBoundsMethods() {
    assertTrue(PxBounds(0, 0, 0, 0).isEmpty())
    assertFalse(PxBounds(0, 0, 0, 0).isNotEmpty())
    assertTrue(PxBounds(0, 0, 0, 1).isEmpty())
    assertFalse(PxBounds(0, 0, 0, 1).isNotEmpty())
    assertTrue(PxBounds(0, 0, 1, 1).isNotEmpty())
    assertFalse(PxBounds(0, 0, 1, 1).isEmpty())

    val testBounds = PxBounds(20, 30, 50, 100)
    assertTrue(testBounds.containsPoint(50, 100))
    assertTrue(testBounds.containsPoint(35, 100))
    assertTrue(testBounds.containsPoint(20, 30))
    assertEquals(2100, testBounds.area())
  }
}

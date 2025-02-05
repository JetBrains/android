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
            ComposeViewInfo(
              TestSourceLocation("fileA"),
              PxBounds(0, 0, 0, 0),
              children = listOf(),
              name = "",
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
                    name = "",
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
                          name = "",
                        )
                      ),
                    name = "",
                  ),
                ),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileC", lineNumber = 10),
              PxBounds(400, 200, 1000, 300),
              children = listOf(),
              name = "",
            ),
          ),
        name = "",
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
  fun checkAllLeafHits() {
    //                        root
    //                      /     \
    //            fileA (10,10)     fileB (20,20)
    //                                \
    //                                fileB (20,10)
    //
    // Given that all the components shown above contain the point x, y calling findAllHits will
    // return both components in file B.
    val root =
      ComposeViewInfo(
        TestSourceLocation("root"),
        PxBounds(0, 0, 1000, 1000),
        children =
          listOf(
            ComposeViewInfo(
              TestSourceLocation("fileA"),
              PxBounds(0, 0, 10, 10),
              children = listOf(),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileB", lineNumber = 4),
              PxBounds(0, 0, 20, 20),
              children =
                listOf(
                  ComposeViewInfo(
                    TestSourceLocation("fileB", lineNumber = 7),
                    PxBounds(0, 0, 10, 20),
                    children = listOf(),
                    name = "",
                  )
                ),
              name = "",
            ),
          ),
        name = "",
      )

    assertTrue(root.findAllLeafHits(10000, 100000).isEmpty())
    val allHits = root.findAllLeafHits(1, 1)
    assertEquals(2, allHits.size)
    assertEquals(allHits.first().sourceLocation.fileName, "fileB")
    assertEquals(allHits.first().bounds.bottom, 20)
    assertEquals(allHits.first().bounds.right, 10)
    assertEquals(allHits.get(1).sourceLocation.fileName, "fileA")
    assertEquals(allHits.get(1).bounds.bottom, 10)
    assertEquals(allHits.get(1).bounds.right, 10)
  }

  @Test
  fun checkAllHitsInFile() {
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
              name = "",
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
                    name = "",
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
                          name = "",
                        )
                      ),
                    name = "",
                  ),
                ),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileC", lineNumber = 10),
              PxBounds(400, 200, 1000, 300),
              children = listOf(),
              name = "",
            ),
          ),
        name = "",
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

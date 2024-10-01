/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.layoutinspector.model.RecompositionData
import com.android.tools.idea.layoutinspector.model.VIEW1
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableNode
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableRoot
import com.android.tools.idea.layoutinspector.pipeline.appinspection.dsl.ComposableString
import com.google.common.truth.Truth.assertThat
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.ComposableNode.Flags
import org.junit.Test

class ComposeViewNodeCreatorTest {

  private val composeStrings =
    listOf(
      ComposableString(1, "com.example"),
      ComposableString(2, "File1.kt"),
      ComposableString(3, "File2.kt"),
      ComposableString(4, "MyTheme"),
      ComposableString(5, "MyApp"),
      ComposableString(6, "MyBackground"),
      ComposableString(7, "Surface"),
      ComposableString(8, "Column"),
      ComposableString(9, "Button"),
      ComposableString(10, "Text"),
      ComposableString(11, "BasicText"),
      ComposableString(12, "Layout"),
      ComposableString(13, "Row"),
      ComposableString(14, "Box"),
    )

  private fun stringId(name: String): Int = composeStrings.single { it.str == name }.id

  //                          Recompositions
  // MyTheme(-2)                   0
  //   MyApp(-3)                   4
  //     MyBackground(-4)          0
  //       Surface(-5)             0
  //         Column(-6)            2
  //           Button(-7)          5
  //             Text(-8)          7
  //               BasicText(-9)   8
  //                 Layout(-10)   9
  //           Row(-11)            3
  //             Box(-12)          3
  private val response =
    LayoutInspectorComposeProtocol.GetComposablesResponse.newBuilder().apply {
      addAllStrings(composeStrings)
      ComposableRoot {
        viewId = VIEW1
        ComposableNode {
          id = -2
          anchorHash = 101
          packageHash = stringId("com.example")
          filename = stringId("File1.kt")
          name = stringId("MyTheme")
          lineNumber = 45
          recomposeCount = 0
          recomposeSkips = 0
          flags = Flags.NESTED_SINGLE_CHILDREN_VALUE

          ComposableNode {
            id = -3
            anchorHash = 102
            packageHash = stringId("com.example")
            filename = stringId("File1.kt")
            name = stringId("MyApp")
            lineNumber = 55
            recomposeCount = 4
            recomposeSkips = 0
          }
          ComposableNode {
            id = -4
            anchorHash = 103
            packageHash = stringId("com.example")
            filename = stringId("File1.kt")
            name = stringId("MyBackground")
            lineNumber = 65
            recomposeCount = 0
            recomposeSkips = 0
          }
          ComposableNode {
            id = -5
            anchorHash = 104
            packageHash = stringId("com.example")
            filename = stringId("File1.kt")
            name = stringId("Surface")
            lineNumber = 75
            recomposeCount = 0
            recomposeSkips = 0
          }
          ComposableNode {
            id = -6
            anchorHash = 104
            packageHash = stringId("com.example")
            filename = stringId("File2.kt")
            name = stringId("Column")
            lineNumber = 12
            recomposeCount = 2
            recomposeSkips = 1

            ComposableNode {
              id = -7
              anchorHash = 105
              packageHash = stringId("com.example")
              filename = stringId("File2.kt")
              name = stringId("Button")
              lineNumber = 22
              recomposeCount = 5
              recomposeSkips = 0

              ComposableNode {
                id = -8
                anchorHash = 106
                packageHash = stringId("com.example")
                filename = stringId("File2.kt")
                name = stringId("Text")
                lineNumber = 32
                recomposeCount = 7
                recomposeSkips = 0

                ComposableNode {
                  id = -9
                  anchorHash = 107
                  packageHash = stringId("com.example")
                  filename = stringId("File2.kt")
                  name = stringId("BasicText")
                  lineNumber = 42
                  recomposeCount = 8
                  recomposeSkips = 2

                  ComposableNode {
                    id = -10
                    anchorHash = 108
                    packageHash = stringId("com.example")
                    filename = stringId("File2.kt")
                    name = stringId("Layout")
                    lineNumber = 52
                    recomposeCount = 9
                    recomposeSkips = 0
                  }
                }
              }
            }
            ComposableNode {
              id = -11
              anchorHash = 109
              packageHash = stringId("com.example")
              filename = stringId("File2.kt")
              name = stringId("Row")
              lineNumber = 62
              recomposeCount = 3
              recomposeSkips = 4

              ComposableNode {
                id = -12
                anchorHash = 109
                packageHash = stringId("com.example")
                filename = stringId("File2.kt")
                name = stringId("Box")
                lineNumber = 72
                recomposeCount = 3
                recomposeSkips = 4
              }
            }
          }
        }
      }
    }

  @Test
  fun testRecompositionCounts() {
    val creator = ComposeViewNodeCreator(GetComposablesResult(response.build(), false))
    val result = creator.createForViewId(VIEW1) { false }!!
    assertThat(result.size).isEqualTo(1)
    val nodes = ViewNode.readAccess { result[0].preOrderFlatten().toList() }
    nodes[0].assertNode("MyTheme", RecompositionData(0, 0, 9), listOf(-3))
    nodes[1].assertNode("MyApp", RecompositionData(4, 0, 9), listOf(-4))
    nodes[2].assertNode("MyBackground", RecompositionData(0, 0, 9), listOf(-5))
    nodes[3].assertNode("Surface", RecompositionData(0, 0, 9), listOf(-6))
    nodes[4].assertNode("Column", RecompositionData(2, 1, 9), listOf(-7, -11))
    nodes[5].assertNode("Button", RecompositionData(5, 0, 9), listOf(-8))
    nodes[6].assertNode("Text", RecompositionData(7, 0, 9), listOf(-9))
    nodes[7].assertNode("BasicText", RecompositionData(8, 2, 9), listOf(-10))
    nodes[8].assertNode("Layout", RecompositionData(9, 0, 0), emptyList())
    nodes[9].assertNode("Row", RecompositionData(3, 4, 3), listOf(-12))
    nodes[10].assertNode("Box", RecompositionData(3, 4, 0), emptyList())
  }

  private fun ViewNode.assertNode(
    name: String,
    recompositionData: RecompositionData,
    childIds: List<Long>,
  ) {
    assertThat(qualifiedName).isEqualTo(name)
    assertThat(recompositions.count).named(name).isEqualTo(recompositionData.count)
    assertThat(recompositions.skips).named(name).isEqualTo(recompositionData.skips)
    assertThat(recompositions.childCount).named(name).isEqualTo(recompositionData.childCount)
    assertThat(ViewNode.readAccess { children.size }).named(name).isEqualTo(childIds.size)
    assertThat(ViewNode.readAccess { children.map { it.drawId } }).named(name).isEqualTo(childIds)
  }
}

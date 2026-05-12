/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class HeightSegmentTreeTest {

  @Test
  fun testHeightSegmentTreeBasic() {
    val tree = HeightSegmentTree(10, 100)
    assertThat(tree.itemCount).isEqualTo(10)
    assertThat(tree.totalHeight).isEqualTo(1000)
    assertThat(tree.avgHeight).isEqualTo(100)
    assertThat(tree.measuredCount).isEqualTo(0)
    assertThat(tree.estimatedCount).isEqualTo(10)
  }

  @Test
  fun testHeightSegmentTreeUpdate() {
    val tree = HeightSegmentTree(10, 100)
    tree.update(0, 200)
    assertThat(tree.totalHeight).isEqualTo(1100)
    assertThat(tree.measuredCount).isEqualTo(1)
    assertThat(tree.estimatedCount).isEqualTo(9)
    // avgHeight is (measuredTotal + estimatedTotal) / totalCount
    // (200 + 9 * 100) / 10 = 1100 / 10 = 110
    assertThat(tree.avgHeight).isEqualTo(110)
  }

  @Test
  fun testHeightSegmentTreeTotalHeightBefore() {
    val tree = HeightSegmentTree(10, 100)
    tree.update(0, 50)
    tree.update(1, 150)
    // 0: 50, 1: 150, 2..9: 100
    assertThat(tree.totalHeightBefore(0)).isEqualTo(0)
    assertThat(tree.totalHeightBefore(1)).isEqualTo(50)
    assertThat(tree.totalHeightBefore(2)).isEqualTo(200)
    assertThat(tree.totalHeightBefore(3)).isEqualTo(300)
    assertThat(tree.totalHeightBefore(10)).isEqualTo(1000)
  }

  @Test
  fun testHeightSegmentTreeFindIndexAtOffset() {
    val tree = HeightSegmentTree(10, 100)
    tree.update(0, 50)
    tree.update(1, 150)
    // 0: 50, 1: 150, 2..9: 100

    // Offset 25 is in item 0
    assertThat(tree.findIndexAtOffset(25)).isEqualTo(0 to 25)
    // Offset 50 is in item 1
    assertThat(tree.findIndexAtOffset(50)).isEqualTo(1 to 0)
    // Offset 100 is in item 1
    assertThat(tree.findIndexAtOffset(100)).isEqualTo(1 to 50)
    // Offset 200 is in item 2
    assertThat(tree.findIndexAtOffset(200)).isEqualTo(2 to 0)
    // Offset 950 is in item 9
    assertThat(tree.findIndexAtOffset(950)).isEqualTo(9 to 50)
  }

  @Test
  fun testHeightSegmentTreeGrow() {
    val tree = HeightSegmentTree(5, 100)
    assertThat(tree.itemCount).isEqualTo(5)
    assertThat(tree.capacity).isAtLeast(5)

    tree.growTo(10)
    assertThat(tree.itemCount).isEqualTo(10)
    assertThat(tree.totalHeight).isEqualTo(1000)
  }

  @Test
  fun testHeightSegmentTreeTruncate() {
    val tree = HeightSegmentTree(10, 100)
    tree.update(9, 200)
    assertThat(tree.totalHeight).isEqualTo(1100)

    tree.truncateTo(5)
    assertThat(tree.itemCount).isEqualTo(5)
    assertThat(tree.totalHeight).isEqualTo(500)
  }

  @Test
  fun testHeightSegmentTreeUpdateAll() {
    val tree = HeightSegmentTree(10, 100)
    tree.update(0, 200)
    tree.updateAll { it * 2 }
    assertThat(tree.totalHeight).isEqualTo(2200)
    assertThat(tree.avgHeight).isEqualTo(220)
  }
}

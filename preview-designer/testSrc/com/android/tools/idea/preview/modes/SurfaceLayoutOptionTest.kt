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
package com.android.tools.idea.preview.modes

import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.uibuilder.surface.layout.HeaderPositionableContent
import com.google.common.truth.Truth.assertThat
import java.awt.Dimension
import java.awt.Insets
import org.junit.Test

class SurfaceLayoutOptionTest {

  open class TestPositionableContent(override val organizationGroup: OrganizationGroup?) :
    PositionableContent {
    override val scale = 0.0
    override val x = 0
    override val y = 0
    override val isFocusedContent = false

    override fun getContentSize(dimension: Dimension?) = Dimension(0, 0)

    override fun setLocation(x: Int, y: Int) {}

    override fun getMargin(scale: Double): Insets = Insets(0, 0, 0, 0)
  }

  class HeaderTestPositionableContent(override val organizationGroup: OrganizationGroup?) :
    TestPositionableContent(organizationGroup), HeaderPositionableContent

  @Test
  fun groupByOrganizationId1() {
    // Groups are [0, 0], [1], [2, 2, 2], [3, 4], [5, 5]
    val group0 = OrganizationGroup("0", "0")
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("2", "2")
    val group3 = OrganizationGroup("3", "3")
    val group4 = OrganizationGroup("4", "4")
    val group5 = OrganizationGroup("5", "5")
    val content =
      listOf(
        HeaderTestPositionableContent(group0),
        TestPositionableContent(group0),
        TestPositionableContent(group0),
        TestPositionableContent(group1),
        HeaderTestPositionableContent(group2),
        TestPositionableContent(group2),
        TestPositionableContent(group2),
        TestPositionableContent(group2),
        TestPositionableContent(group3),
        TestPositionableContent(group4),
        HeaderTestPositionableContent(group5),
        TestPositionableContent(group5),
        TestPositionableContent(group5),
      )
    val groups = GROUP_BY_BASE_COMPONENT(content)
    assertThat(groups).hasSize(5)
    assertThat(groups[0].content).containsExactly(content[1], content[2])
    assertThat(groups[1].content).containsExactly(content[3])
    assertThat(groups[2].content).containsExactly(content[5], content[6], content[7])
    assertThat(groups[3].content).containsExactly(content[8], content[9])
    assertThat(groups[4].content).containsExactly(content[11], content[12])
    assertThat(groups[0].header).isNotNull()
    assertThat(groups[1].header).isNull()
    assertThat(groups[2].header).isNotNull()
    assertThat(groups[3].header).isNull()
    assertThat(groups[4].header).isNotNull()
  }

  @Test
  fun groupByOrganizationId2() {
    // Groups are [0, 1, 2]
    val group0 = OrganizationGroup("0", "0")
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("2", "2")
    val content =
      listOf(
        TestPositionableContent(group0),
        TestPositionableContent(group1),
        TestPositionableContent(group2),
      )
    val groups = GROUP_BY_BASE_COMPONENT(content)
    assertThat(groups).hasSize(1)
    assertThat(groups[0].content).containsExactly(content[0], content[1], content[2])
    assertThat(groups[0].header).isNull()
  }

  @Test
  fun groupByOrganizationId3() {
    // Groups are [0]
    val group0 = OrganizationGroup("0", "0")
    val content = listOf(TestPositionableContent(group0))
    val groups = GROUP_BY_BASE_COMPONENT(content)
    assertThat(groups).hasSize(1)
    assertThat(groups[0].content).containsExactly(content[0])
    assertThat(groups[0].header).isNull()
  }

  @Test
  fun groupByOrganizationId4() {
    // Groups are [0]
    val group0 = OrganizationGroup("0", "0")
    val content = listOf(TestPositionableContent(group0), HeaderTestPositionableContent(group0))
    val groups = GROUP_BY_BASE_COMPONENT(content)
    assertThat(groups).hasSize(1)
    assertThat(groups[0].content).containsExactly(content[0])
    assertThat(groups[0].header).isEqualTo(content[1])
  }

  @Test
  fun groupByOrganizationId5() {
    // Groups are [0, 1], [2, 2]
    val group0 = OrganizationGroup("0", "0")
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("2", "2")
    val content =
      listOf(
        TestPositionableContent(group0),
        TestPositionableContent(group1),
        HeaderTestPositionableContent(group2),
        TestPositionableContent(group2),
        TestPositionableContent(group2),
      )
    val groups = GROUP_BY_BASE_COMPONENT(content)
    assertThat(groups).hasSize(2)
    assertThat(groups[0].content).containsExactly(content[0], content[1])
    assertThat(groups[1].content).containsExactly(content[3], content[4])
    assertThat(groups[0].header).isNull()
    assertThat(groups[1].header).isEqualTo(content[2])
  }

  @Test
  fun headerWithoutContent() {
    // Groups are [], [2, 2]
    val group0 = OrganizationGroup("0", "0")
    val group2 = OrganizationGroup("2", "2")
    val content =
      listOf(
        HeaderTestPositionableContent(group0),
        HeaderTestPositionableContent(group2),
        TestPositionableContent(group2),
        TestPositionableContent(group2),
      )
    val groups = GROUP_BY_BASE_COMPONENT(content)
    assertThat(groups).hasSize(2)
    assertThat(groups[0].content).isEmpty()
    assertThat(groups[1].content).containsExactly(content[2], content[3])
    assertThat(groups[0].header).isEqualTo(content[0])
    assertThat(groups[1].header).isEqualTo(content[1])
  }

  @Test
  fun headerWithoutContent2() {
    // Groups are [], [], []
    val group0 = OrganizationGroup("0", "0")
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("2", "2")
    val content =
      listOf(
        HeaderTestPositionableContent(group0),
        HeaderTestPositionableContent(group1),
        HeaderTestPositionableContent(group2),
      )
    val groups = GROUP_BY_BASE_COMPONENT(content)
    assertThat(groups).hasSize(3)
    assertThat(groups[0].content).isEmpty()
    assertThat(groups[1].content).isEmpty()
    assertThat(groups[2].content).isEmpty()
    assertThat(groups[0].header).isEqualTo(content[0])
    assertThat(groups[1].header).isEqualTo(content[1])
    assertThat(groups[2].header).isEqualTo(content[2])
  }

  @Test
  fun emptyGroups() {
    val groups = GROUP_BY_BASE_COMPONENT(emptyList())
    assertThat(groups).hasSize(0)
  }
}

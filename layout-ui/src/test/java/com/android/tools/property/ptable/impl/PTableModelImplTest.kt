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
package com.android.tools.property.ptable.impl

import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.item.Group
import com.android.tools.property.ptable.item.Item
import com.android.tools.property.ptable.item.createModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PTableModelImplTest {

  @Test
  fun testDepth() {
    val model =
      createModel(
        Item("weight"),
        Group("weiss", Item("siphon"), Group("extra", Item("some"), Group("more", Item("stuff")))),
      )
    val impl = PTableModelImpl(model)
    assertThat(impl.depth(model.find("weight")!!)).isEqualTo(0)
    assertThat(impl.depth(model.find("weiss")!!)).isEqualTo(0)
    assertThat(impl.depth(model.find("siphon")!!)).isEqualTo(1)
    assertThat(impl.depth(model.find("extra")!!)).isEqualTo(1)
    assertThat(impl.depth(model.find("some")!!)).isEqualTo(2)
    assertThat(impl.depth(model.find("more")!!)).isEqualTo(2)
    assertThat(impl.depth(model.find("stuff")!!)).isEqualTo(3)
  }

  @Test
  fun testParentOf() {
    val model =
      createModel(
        Item("weight"),
        Group("weiss", Item("siphon"), Group("extra", Item("some"), Group("more", Item("stuff")))),
      )
    val impl = PTableModelImpl(model)
    assertThat(impl.parentOf(model.find("weight")!!)).isNull()
    assertThat(impl.parentOf(model.find("weiss")!!)).isNull()
    assertThat(impl.parentOf(model.find("siphon")!!)).isEqualTo(model.find("weiss")!!)
    assertThat(impl.parentOf(model.find("extra")!!)).isEqualTo(model.find("weiss")!!)
    assertThat(impl.parentOf(model.find("some")!!)).isEqualTo(model.find("extra")!!)
    assertThat(impl.parentOf(model.find("more")!!)).isEqualTo(model.find("extra")!!)
    assertThat(impl.parentOf(model.find("stuff")!!)).isEqualTo(model.find("more")!!)
  }

  @Test
  fun testRestoreExpandedGroups() {
    val model =
      createModel(
        Item("weight"),
        Item("size"),
        Item("readonly"),
        Item("visible"),
        Group("weiss", Item("siphon"), Item("extra")),
      )
    val groupBefore = model.items[4] as PTableGroupItem
    val impl = PTableModelImpl(model)
    impl.expand(4)
    model.updateTo(
      true,
      Item("weight"),
      Item("size"),
      Item("readonly"),
      Group("weiss", Item("siphon"), Item("extra")),
      Item("zebra"),
    )
    val groupAfter = model.items[3] as PTableGroupItem

    assertThat(impl.isExpanded(groupAfter)).isTrue()
    assertThat(impl.expandedItems).containsExactly(groupAfter)

    // Verify that the old expanded group is no longer in the expandedItems:
    assertThat(impl.expandedItems.first()).isNotSameAs(groupBefore)

    // Verify that the new expanded group is now in the expandedItems:
    assertThat(impl.expandedItems.first()).isSameAs(groupAfter)
  }

  @Test
  fun testCloseGroupWithNestedGroups() {
    val model =
      createModel(
        Group("group", Item("item1"), Group("visibility", Item("show"), Item("hide"))),
        Item("item2"),
      )
    val impl = PTableModelImpl(model)
    impl.expand(0)
    impl.expand(2)
    impl.collapse(0)

    checkRows(impl, "group", "item2")
  }

  @Test
  fun testReopenClosedGroupWithNestedGroups() {
    val model =
      createModel(
        Group("group", Item("item1"), Group("visibility", Item("show"), Item("hide"))),
        Item("item2"),
      )
    val impl = PTableModelImpl(model)
    impl.expand(0)
    impl.expand(2)
    impl.collapse(0)
    impl.expand(0)

    checkRows(impl, "group", "item1", "visibility", "show", "hide", "item2")
  }

  @Test
  fun testRestoreExpandedGroupsWithNestedGroups() {
    val model =
      createModel(
        Group("group", Item("item1"), Group("visibility", Item("show"), Item("hide"))),
        Item("item2"),
      )
    val impl = PTableModelImpl(model)
    impl.expand(0)
    impl.expand(2)
    model.updateTo(
      true,
      Item("extra"),
      Group("group", Item("item1"), Group("visibility", Item("show"), Item("hide"))),
      Item("item2"),
    )

    checkRows(impl, "extra", "group", "item1", "visibility", "show", "hide", "item2")
  }

  @Test
  fun testModelWithMultipleEqualNodes() {
    val model = createModel(Group("group", Group("group", Item("item")), Item("item")))
    val group1 = model.items.single() as Group
    val group2 = group1.children.first() as Group
    val item1 = group1.children.last()
    val item2 = group2.children.single()
    val impl = PTableModelImpl(model)
    assertThat(impl.parentOf(item1)).isSameAs(group1)
    assertThat(impl.parentOf(item2)).isSameAs(group2)
    assertThat(impl.depth(item1)).isEqualTo(1)
    assertThat(impl.depth(item2)).isEqualTo(2)
  }

  @Test
  fun testExpansionWithMultipleEqualGroupNodes() {
    val model =
      createModel(
        Group("top", Group("group", Item("item1")), Group("group", Item("item2")), Item("item3"))
      )
    val top = model.items.single() as Group
    val group1 = top.children.first() as Group
    val group2 = top.children[1] as Group
    val impl = PTableModelImpl(model)
    checkRows(impl, "top")
    impl.toggle(top)
    checkRows(impl, "top", "group", "group", "item3")
    impl.toggle(group1)
    checkRows(impl, "top", "group", "item1", "group", "item3")
    impl.toggle(group2)
    checkRows(impl, "top", "group", "item1", "group", "item2", "item3")
    impl.toggle(group1)
    checkRows(impl, "top", "group", "group", "item2", "item3")
    impl.toggle(group2)
    checkRows(impl, "top", "group", "group", "item3")
    impl.toggle(top)
    checkRows(impl, "top")
  }

  @Test
  fun testDelayedExpansion() {
    val model = createModel(Item("weight"), Group("weiss"))
    val weiss = model.find("weiss") as Group
    weiss.delayedExpansion = true

    val impl = PTableModelImpl(model)
    checkRows(impl, "weight", "weiss")

    impl.toggle(weiss)
    checkRows(impl, "weight", "weiss")

    weiss.children.addAll(
      listOf(Item("siphon"), Group("extra", Item("some"), Group("more", Item("stuff"))))
    )
    checkRows(impl, "weight", "weiss")

    weiss.expandNow(true)
    checkRows(impl, "weight", "weiss", "siphon", "extra")

    val more = model.find("more") as Group
    assertThat(impl.depth(more)).isEqualTo(2)
  }

  private fun checkRows(model: PTableModelImpl, vararg titles: String) {
    assertThat(model.rowCount).isEqualTo(titles.size)
    for ((index, title) in titles.withIndex()) {
      assertThat(model.getValueAt(index, 0).name).isEqualTo(title)
    }
  }
}

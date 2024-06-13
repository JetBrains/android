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
package com.android.tools.property.panel.impl.model

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_GRAVITY
import com.android.tools.property.panel.api.FlagPropertyItem
import com.android.tools.property.panel.api.FlagsPropertyItem
import com.android.tools.property.panel.impl.model.util.FakeFlagsPropertyItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlagPropertyEditorModelTest {

  @Test
  fun testEmptyInitialItemsAboveSeparator() {
    val autoLink = createAutoLink()
    val model = createModel(autoLink)
    assertThat(model.initialItemsAboveSeparator).isEmpty()
  }

  @Test
  fun testInitialItemsAboveSeparator() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    assertThat(model.initialItemsAboveSeparator).containsExactly("phone", "map").inOrder()
  }

  @Test
  fun testEmptyInitialItemsBelowSeparator() {
    val autoLink = createAutoLink()
    val model = createModel(autoLink)
    assertThat(model.initialItemsBelowSeparator)
      .containsExactly("none", "phone", "map", "perm", "all")
      .inOrder()
  }

  @Test
  fun testInitialItemsBelowSeparator() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    assertThat(model.initialItemsBelowSeparator).containsExactly("none", "perm", "all").inOrder()
  }

  @Test
  fun testEmptyFlagDividerVisible() {
    val autoLink = createAutoLink()
    val model = createModel(autoLink)
    assertThat(model.flagDividerVisible).isFalse()
  }

  @Test
  fun testFlagDividerVisible() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    assertThat(model.flagDividerVisible).isTrue()
  }

  @Test
  fun testFullFlagDividerVisible() {
    val autoLink = createAutoLink("none|phone|map|perm|all")
    val model = createModel(autoLink)
    assertThat(model.flagDividerVisible).isFalse()
  }

  @Test
  fun testFlagDividerVisibleWithFilterMatchingAboveAndBelow() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.filter = "p"
    assertThat(model.flagDividerVisible).isTrue()
  }

  @Test
  fun testFlagDividerVisibleWithFilterMatchingAbove() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.filter = "ma"
    assertThat(model.flagDividerVisible).isFalse()
  }

  @Test
  fun testFlagDividerVisibleWithFilterMatchingBelow() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.filter = "no"
    assertThat(model.flagDividerVisible).isFalse()
  }

  @Test
  fun testFlagDividerVisibleWithFilterNoMatches() {
    val autoLink = createAutoLink("")
    val model = createModel(autoLink)
    model.filter = "non-existing-filter"
    assertThat(model.flagDividerVisible).isFalse()
  }

  @Test
  fun testToggle() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.toggle("phone")
    model.toggle("perm")
    model.applyChanges()
    assertThat(autoLink.value).isEqualTo("map|perm")
  }

  @Test
  fun testSelected() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    assertThat(model.isSelected("none")).isFalse()
    assertThat(model.isSelected("phone")).isTrue()
    assertThat(model.isSelected("map")).isTrue()
    assertThat(model.isSelected("perm")).isFalse()
    assertThat(model.isSelected("all")).isFalse()
    assertThat(model.isSelected("non-existing-flag")).isFalse()
  }

  @Test
  fun testSelectedAfterAllToggle() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.toggle("all")
    assertThat(model.isSelected("none")).isFalse()
    assertThat(model.isSelected("phone")).isTrue()
    assertThat(model.isSelected("map")).isTrue()
    assertThat(model.isSelected("perm")).isTrue()
    assertThat(model.isSelected("all")).isTrue()
  }

  @Test
  fun testEmptyEnabled() {
    val autoLink = createAutoLink()
    val model = createModel(autoLink)
    assertThat(model.isEnabled("none")).isTrue()
    assertThat(model.isEnabled("phone")).isTrue()
    assertThat(model.isEnabled("map")).isTrue()
    assertThat(model.isEnabled("perm")).isTrue()
    assertThat(model.isEnabled("all")).isTrue()
    assertThat(model.isEnabled("non-existing-flag")).isFalse()
  }

  @Test
  fun testEnabled() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    assertThat(model.isEnabled("none")).isFalse()
    assertThat(model.isEnabled("phone")).isTrue()
    assertThat(model.isEnabled("map")).isTrue()
    assertThat(model.isEnabled("perm")).isTrue()
    assertThat(model.isEnabled("all")).isTrue()
    assertThat(model.isEnabled("non-existing-flag")).isFalse()
  }

  @Test
  fun testEnabledAfterNoneToggle() {
    val autoLink = createAutoLink()
    val model = createModel(autoLink)
    model.toggle("none")
    assertThat(model.isEnabled("none")).isTrue()
    assertThat(model.isEnabled("phone")).isFalse()
    assertThat(model.isEnabled("map")).isFalse()
    assertThat(model.isEnabled("perm")).isFalse()
    assertThat(model.isEnabled("all")).isFalse()
  }

  @Test
  fun testEnabledAfterAllToggle() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.toggle("all")
    assertThat(model.isEnabled("none")).isFalse()
    assertThat(model.isEnabled("phone")).isTrue()
    assertThat(model.isEnabled("map")).isTrue()
    assertThat(model.isEnabled("perm")).isFalse()
    assertThat(model.isEnabled("all")).isTrue()
  }

  @Test
  fun testVisible() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    assertThat(model.isVisible("none")).isTrue()
    assertThat(model.isVisible("phone")).isTrue()
    assertThat(model.isVisible("map")).isTrue()
    assertThat(model.isVisible("perm")).isTrue()
    assertThat(model.isVisible("all")).isTrue()
  }

  @Test
  fun testVisibleWithFilter() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.filter = "p"
    assertThat(model.isVisible("none")).isFalse()
    assertThat(model.isVisible("phone")).isTrue()
    assertThat(model.isVisible("map")).isFalse()
    assertThat(model.isVisible("perm")).isTrue()
    assertThat(model.isVisible("all")).isFalse()
    assertThat(model.isVisible("non-existing-flag")).isFalse()
  }

  @Test
  fun testSelectAll() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.selectAll()
    assertThat(model.isSelected("none")).isFalse()
    assertThat(model.isSelected("phone")).isTrue()
    assertThat(model.isSelected("map")).isTrue()
    assertThat(model.isSelected("perm")).isTrue()
    assertThat(model.isSelected("all")).isTrue()

    model.applyChanges()
    assertThat(autoLink.value).isEqualTo("all")
  }

  @Test
  fun testSelectAllWithoutAllValue() {
    val gravity = createGravity("center")
    val model = createModel(gravity)
    model.selectAll()
    assertThat(model.isSelected("left")).isTrue()
    assertThat(model.isSelected("center")).isTrue()
    assertThat(model.isSelected("right")).isTrue()

    model.applyChanges()
    assertThat(gravity.value).isEqualTo("left|center|right")
  }

  @Test
  fun testClearAll() {
    val autoLink = createAutoLink("phone|map")
    val model = createModel(autoLink)
    model.clearAll()
    assertThat(model.isSelected("none")).isTrue()
    assertThat(model.isSelected("phone")).isFalse()
    assertThat(model.isSelected("map")).isFalse()
    assertThat(model.isSelected("perm")).isFalse()
    assertThat(model.isSelected("all")).isFalse()

    model.applyChanges()
    assertThat(autoLink.value).isEqualTo("none")
  }

  @Test
  fun testClearAllWithoutNoneValue() {
    val gravity = createGravity("center")
    val model = createModel(gravity)
    model.clearAll()
    assertThat(model.isSelected("left")).isFalse()
    assertThat(model.isSelected("center")).isFalse()
    assertThat(model.isSelected("right")).isFalse()

    model.applyChanges()
    assertThat(gravity.value).isNull()
  }

  @Test
  fun testSelectAllWithFilterSet() {
    val autoLink = createAutoLink("phone|map|perm|all")
    val model = createModel(autoLink)
    model.filter = "p"
    model.clearAll()
    assertThat(model.isSelected("none")).isFalse()
    assertThat(model.isSelected("phone")).isTrue()
    assertThat(model.isEnabled("phone")).isFalse()
    assertThat(model.isSelected("map")).isTrue()
    assertThat(model.isSelected("perm")).isTrue()
    assertThat(model.isEnabled("perm")).isFalse()
    assertThat(model.isSelected("all")).isTrue()

    model.applyChanges()
    assertThat(autoLink.value).isEqualTo("map|all")
  }

  @Test
  fun testClearAllWithFilterSet() {
    val autoLink = createAutoLink()
    val model = createModel(autoLink)
    model.filter = "p"
    model.selectAll()
    assertThat(model.isSelected("none")).isFalse()
    assertThat(model.isSelected("phone")).isTrue()
    assertThat(model.isSelected("map")).isFalse()
    assertThat(model.isSelected("perm")).isTrue()
    assertThat(model.isSelected("all")).isFalse()

    model.applyChanges()
    assertThat(autoLink.value).isEqualTo("phone|perm")
  }

  private fun createAutoLink(initialValue: String? = null): FlagsPropertyItem<FlagPropertyItem> {
    return FakeFlagsPropertyItem(
      ANDROID_URI,
      "autoLink",
      listOf("none", "phone", "map", "perm", "all"),
      listOf(0, 1, 2, 4, 7),
      initialValue,
    )
  }

  private fun createGravity(initialValue: String? = null): FlagsPropertyItem<FlagPropertyItem> {
    return FakeFlagsPropertyItem(
      ANDROID_URI,
      ATTR_GRAVITY,
      listOf("left", "center", "right"),
      listOf(1, 2, 4),
      initialValue,
    )
  }

  private fun createModel(property: FlagsPropertyItem<*>): FlagPropertyEditorModel {
    val model = FlagPropertyEditorModel(property)
    // Initialize dialog data:
    model.initialItemsAboveSeparator
    return model
  }
}

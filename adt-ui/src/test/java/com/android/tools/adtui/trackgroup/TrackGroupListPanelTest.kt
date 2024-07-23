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
package com.android.tools.adtui.trackgroup

import com.android.tools.adtui.TooltipView
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.DefaultTimeline
import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.TooltipModel
import com.android.tools.adtui.model.trackgroup.BooleanSelectable
import com.android.tools.adtui.model.trackgroup.StringSelectable
import com.android.tools.adtui.model.trackgroup.TestTrackRendererType
import com.android.tools.adtui.model.trackgroup.TrackGroupModel
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JLabel

@RunsInEdt
class TrackGroupListPanelTest {
  companion object {
    val TRACK_RENDERER_FACTORY = TestTrackRendererFactory()

    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Test
  fun loadTrackGroups() {
    val trackGroupListPanel = TrackGroupListPanel(TRACK_RENDERER_FACTORY)
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("Group").build()

    assertThat(trackGroupListPanel.component.componentCount).isEqualTo(0)
    trackGroupListPanel.loadTrackGroups(listOf(trackGroupModel))
    assertThat(trackGroupListPanel.component.componentCount).isEqualTo(1)
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[0] as JComponent)).isEqualTo("Group")
  }

  @Test
  fun moveTrackGroupUp() {
    val trackGroupListPanel = TrackGroupListPanel(TRACK_RENDERER_FACTORY)
    val trackGroupModel1 = TrackGroupModel.newBuilder().setTitle("Group1").build()
    val trackGroupModel2 = TrackGroupModel.newBuilder().setTitle("Group2").build()
    trackGroupListPanel.loadTrackGroups(listOf(trackGroupModel1, trackGroupModel2))

    assertThat(trackGroupListPanel.component.componentCount).isEqualTo(2)
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[0] as JComponent)).isEqualTo("Group1")
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[1] as JComponent)).isEqualTo("Group2")

    trackGroupListPanel.moveTrackGroupUp(trackGroupListPanel.trackGroups[1])
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[0] as JComponent)).isEqualTo("Group2")
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[1] as JComponent)).isEqualTo("Group1")
  }

  @Test
  fun moveTrackGroupDown() {
    val trackGroupListPanel = TrackGroupListPanel(TRACK_RENDERER_FACTORY)
    val trackGroupModel1 = TrackGroupModel.newBuilder().setTitle("Group1").build()
    val trackGroupModel2 = TrackGroupModel.newBuilder().setTitle("Group2").build()
    trackGroupListPanel.loadTrackGroups(listOf(trackGroupModel1, trackGroupModel2))

    assertThat(trackGroupListPanel.component.componentCount).isEqualTo(2)
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[0] as JComponent)).isEqualTo("Group1")
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[1] as JComponent)).isEqualTo("Group2")

    trackGroupListPanel.moveTrackGroupDown(trackGroupListPanel.trackGroups[0])
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[0] as JComponent)).isEqualTo("Group2")
    assertThat(getTrackGroupTitle(trackGroupListPanel.component.components[1] as JComponent)).isEqualTo("Group1")
  }

  @Test
  fun multiSelectionModel() {
    val multiSectionModel = MultiSelectionModel<StringSelectable>()
    val trackGroupListPanel = TrackGroupListPanel(TRACK_RENDERER_FACTORY)
    // Group 1 is multi-selectable.
    val trackGroupModel1 = TrackGroupModel.newBuilder().setTitle("Group1")
      .setSelector(TrackGroupModel.makeBatchSelector("tag1")).build()
    trackGroupModel1.addTrackModel(
      TrackModel.newBuilder(StringSelectable("Bar1"), TestTrackRendererType.STRING_SELECTABLE, "Group1 - Bar1"))
    trackGroupModel1.addTrackModel(
      TrackModel.newBuilder(StringSelectable("Bar2"), TestTrackRendererType.STRING_SELECTABLE, "Group1 - Bar2"))
    // Group 2 is not selectable.
    val trackGroupModel2 = TrackGroupModel.newBuilder().setTitle("Group2").build()
    trackGroupModel2.addTrackModel(TrackModel.newBuilder("Bar1", TestTrackRendererType.STRING, "Group2 - Bar1"))
    // Group 3 is multi-selectable but contains a different type.
    val trackGroupModel3 = TrackGroupModel.newBuilder().setTitle("Group3")
      .setSelector(TrackGroupModel.makeBatchSelector("tag3")).build()
    trackGroupModel3.addTrackModel(
      TrackModel.newBuilder(BooleanSelectable(true), TestTrackRendererType.BOOLEAN_SELECTABLE, "Group3 - Bar1"))

    trackGroupListPanel.loadTrackGroups(listOf(trackGroupModel1, trackGroupModel2, trackGroupModel3))
    trackGroupListPanel.registerMultiSelectionModel(multiSectionModel)

    fun selectionBy(tag: String) = multiSectionModel.selections.find { it.key == tag }?.value

    // Selecting items should update the multi-selection model.
    trackGroupListPanel.trackGroups[0].trackList.selectedIndices = intArrayOf(0, 1)
    assertThat(selectionBy("tag1")).containsExactly(StringSelectable("Bar1"), StringSelectable("Bar2"))
    assertThat(selectionBy("tag2")).isNull()
    assertThat(selectionBy("tag3")).isNull()

    // Selecting an unselectable track group should not update the multi-selection model.
    multiSectionModel.clearSelection()
    trackGroupListPanel.trackGroups[1].trackList.selectedIndex = 0
    assertThat(selectionBy("tag1")).isNull()
    assertThat(selectionBy("tag2")).isNull()
    assertThat(selectionBy("tag3")).isNull()

    // Selecting a different selectable type
    trackGroupListPanel.trackGroups[0].trackList.selectedIndex = 0
    trackGroupListPanel.trackGroups[2].trackList.selectedIndex = 0
    assertThat(selectionBy("tag1")).containsExactly(StringSelectable("Bar1"))
    assertThat(selectionBy("tag2")).isNull()
    assertThat(selectionBy("tag3")).containsExactly(BooleanSelectable(true))
  }

  @Test
  fun showTooltip() {
    val trackGroupListPanel = TrackGroupListPanel(TRACK_RENDERER_FACTORY)
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("Group1").build()
    trackGroupModel.addTrackModel(TrackModel.newBuilder(true, TestTrackRendererType.BOOLEAN, "Foo").setDefaultTooltipModel(TestTooltip()))
    trackGroupListPanel.tooltipBinder.bind(TestTooltip::class.java, ::TestTooltipView)
    trackGroupListPanel.loadTrackGroups(listOf(trackGroupModel))

    assertThat(trackGroupListPanel.activeTooltip).isNull()
    val trackGroupOverlay = trackGroupListPanel.trackGroups[0].overlay
    trackGroupOverlay.setBounds(0, 0, 500, 100)
    val ui = FakeUi(trackGroupOverlay)
    ui.mouse.moveTo(1, 1)
    assertThat(trackGroupListPanel.activeTooltip).isInstanceOf(TestTooltip::class.java)
  }

  private fun getTrackGroupTitle(component: JComponent): String {
    val treeWalker = TreeWalker(component)
    val titleLabel = treeWalker.descendants().filterIsInstance(JLabel::class.java).first()
    return titleLabel.text
  }

  private class TestTooltip : TooltipModel {
    val timeline = DefaultTimeline()
  }

  private class TestTooltipView(@Suppress("UNUSED_PARAMETER") parent: JComponent, tooltip: TestTooltip) : TooltipView(tooltip.timeline) {
    override fun createTooltip(): JComponent {
      return JLabel()
    }
  }
}
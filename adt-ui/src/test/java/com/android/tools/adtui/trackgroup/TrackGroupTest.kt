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

import com.android.tools.adtui.model.MultiSelectionModel
import com.android.tools.adtui.model.trackgroup.StringSelectable
import com.android.tools.adtui.model.trackgroup.TestTrackRendererType
import com.android.tools.adtui.model.trackgroup.TrackGroupModel
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.android.tools.adtui.swing.FakeKeyboard
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TrackGroupTest {
  companion object {
    val TRACK_RENDERER_FACTORY = TestTrackRendererFactory()
  }

  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun createTrackGroup() {
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("Group").build()
    trackGroupModel.addTrackModel(TrackModel.newBuilder(true, TestTrackRendererType.BOOLEAN, "Foo"))
    trackGroupModel.addTrackModel(TrackModel.newBuilder("text", TestTrackRendererType.STRING, "Bar"))
    val trackGroup = TrackGroup(trackGroupModel, TRACK_RENDERER_FACTORY)

    assertThat(trackGroup.titleLabel.text).isEqualTo("Group")
    assertThat(trackGroup.trackList.model.size).isEqualTo(2)
    assertThat(trackGroup.isEmpty).isFalse()
    assertThat(trackGroup.getTrackModelAt(0).dataModel).isEqualTo(true)
    assertThat(trackGroup.getTrackModelAt(1).dataModel).isEqualTo("text")
  }

  @Test
  fun collapseAndExpandTrackGroup() {
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("Group").setCollapsedInitially(true).build()
    val trackGroup = TrackGroup(trackGroupModel, TRACK_RENDERER_FACTORY)

    assertThat(trackGroup.trackList.isVisible).isFalse()
    assertThat(trackGroup.actionsDropdown.isVisible).isFalse()
    assertThat(trackGroup.actionsDropdown.toolTipText).isEqualTo("More actions")
    assertThat(trackGroup.separator.isVisible).isFalse()
    assertThat(trackGroup.collapseButton.text).isEqualTo("Expand Section")

    trackGroup.setCollapsed(false)
    assertThat(trackGroup.trackList.isVisible).isTrue()
    assertThat(trackGroup.actionsDropdown.isVisible).isTrue()
    assertThat(trackGroup.separator.isVisible).isFalse()
    assertThat(trackGroup.collapseButton.text).isNull()

    trackGroup.setCollapsed(true)
    assertThat(trackGroup.trackList.isVisible).isFalse()
    assertThat(trackGroup.actionsDropdown.isVisible).isFalse()
    assertThat(trackGroup.separator.isVisible).isFalse()
    assertThat(trackGroup.collapseButton.text).isEqualTo("Expand Section")
  }

  @Test
  fun hideTrackGroupHeader() {
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("Group").setHideHeader(true).build()
    trackGroupModel.addTrackModel(TrackModel.newBuilder("text", TestTrackRendererType.STRING, "Bar"))
    val trackGroup = TrackGroup(trackGroupModel, TRACK_RENDERER_FACTORY)

    assertThat(trackGroup.titleLabel.parent).isNull()
  }

  @Test
  fun headerInfoTooltip() {
    val noInfoTrackGroupModel = TrackGroupModel.newBuilder().setTitle("Foo").build()
    val noInfoTrackGroup = TrackGroup(noInfoTrackGroupModel, TRACK_RENDERER_FACTORY)
    assertThat(noInfoTrackGroup.titleInfoIcon.isVisible).isFalse()
    assertThat(noInfoTrackGroup.titleInfoIcon.toolTipText).isNull()

    val infoTrackGroupModel = TrackGroupModel.newBuilder().setTitle("Bar").setTitleInfo("Information").build();
    val infoTrackGroup = TrackGroup(infoTrackGroupModel, TestTrackRendererFactory())
    assertThat(infoTrackGroup.titleInfoIcon.isVisible).isTrue()
    assertThat(infoTrackGroup.titleInfoIcon.toolTipText).isEqualTo("Information")
  }

  @Test
  fun keyboardExpandCollapseTrackListItemTest() {
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("Group1").setTrackSelectable(true).build()

    // build two track models both of which are collapsible and initially in a collapsed state
    val trackModel1 = TrackModel.newBuilder(StringSelectable("Bar1"), TestTrackRendererType.STRING_SELECTABLE, "Group1 - Bar1")
      .setCollapsible(true)
      .setCollapsed(true)
    val trackModel2 = TrackModel.newBuilder(StringSelectable("Bar2"), TestTrackRendererType.STRING_SELECTABLE, "Group1 - Bar2")
      .setCollapsible(true)
      .setCollapsed(true)
    trackGroupModel.addTrackModel(trackModel1)
    trackGroupModel.addTrackModel(trackModel2)

    val trackGroup = TrackGroup(trackGroupModel, TRACK_RENDERER_FACTORY)

    val ui = FakeUi(trackGroup.component)

    // test multi selection expand/collapse

    trackGroup.trackList.selectedIndices = intArrayOf(0, 1)
    ui.keyboard.setFocus(trackGroup.trackList)

    ui.keyboard.press(FakeKeyboard.Key.ENTER)
    ui.keyboard.release(FakeKeyboard.Key.ENTER)

    assertThat(trackGroupModel[0].isCollapsed).isFalse()
    assertThat(trackGroupModel[1].isCollapsed).isFalse()

    ui.keyboard.press(FakeKeyboard.Key.ENTER)
    ui.keyboard.release(FakeKeyboard.Key.ENTER)

    assertThat(trackGroupModel[0].isCollapsed).isTrue()
    assertThat(trackGroupModel[1].isCollapsed).isTrue()

    // test single selection expand/collapse

    trackGroup.trackList.selectedIndices = intArrayOf(0)

    ui.keyboard.press(FakeKeyboard.Key.ENTER)
    ui.keyboard.release(FakeKeyboard.Key.ENTER)

    assertThat(trackGroupModel[0].isCollapsed).isFalse()
    assertThat(trackGroupModel[1].isCollapsed).isTrue()

    ui.keyboard.press(FakeKeyboard.Key.ENTER)
    ui.keyboard.release(FakeKeyboard.Key.ENTER)

    assertThat(trackGroupModel[0].isCollapsed).isTrue()
    assertThat(trackGroupModel[1].isCollapsed).isTrue()
  }
}
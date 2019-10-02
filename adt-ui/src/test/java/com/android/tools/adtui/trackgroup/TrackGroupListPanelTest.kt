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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.trackgroup.TrackGroupModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JLabel

class TrackGroupListPanelTest {
  companion object {
    val TRACK_RENDERER_FACTORY = TestTrackRendererFactory()
  }

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

  private fun getTrackGroupTitle(component: JComponent): String {
    val treeWalker = TreeWalker(component)
    val titleLabel = treeWalker.descendants().filterIsInstance(JLabel::class.java).first()
    return titleLabel.text
  }
}
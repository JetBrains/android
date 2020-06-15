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

import com.android.tools.adtui.common.contentSelectionBackground
import com.android.tools.adtui.model.trackgroup.TestTrackRendererType
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import org.junit.Test
import javax.swing.JLabel
import javax.swing.UIManager

class TrackTest {

  @Test
  fun createTrack() {
    val trackModel = TrackModel.newBuilder(true, TestTrackRendererType.BOOLEAN, "foo").build()
    val track = Track.create(trackModel, BooleanTrackRenderer())
    assertThat(track.titleLabel.text).isEqualTo("foo")
    assertThat(track.component.componentCount).isEqualTo(2)
    assertThat(track.component.getComponent(1)).isInstanceOf(JLabel::class.java)
  }

  @Test
  fun hideTrackHeader() {
    val trackModel = TrackModel.newBuilder(true, TestTrackRendererType.BOOLEAN, "foo").setHideHeader(true).build()
    val trackComponent = Track.create(trackModel, BooleanTrackRenderer()).component
    assertThat(trackComponent.componentCount).isEqualTo(1)
    assertThat(trackComponent.getComponent(0)).isInstanceOf(JLabel::class.java)
  }

  @Test
  fun updateSelected() {
    val trackModel = TrackModel.newBuilder(true, TestTrackRendererType.BOOLEAN, "foo").setCollapsible(false).build()
    val track = Track.create(trackModel, BooleanTrackRenderer())
    assertThat(track.component.background).isEqualTo(UIManager.getColor("Panel.background"))
    track.updateSelected(true)
    assertThat(track.titleFrontPanel.background).isEqualTo(contentSelectionBackground)
    assertThat(track.titleLabel.icon).isNull()
  }

  @Test
  fun collapseTrack() {
    val trackModel = TrackModel.newBuilder(true, TestTrackRendererType.BOOLEAN, "foo")
      .setCollapsible(true)
      .setCollapsed(false)
      .build()
    val collapsedTrack = Track.create(trackModel, BooleanTrackRenderer())
    assertThat(collapsedTrack.titleLabel.icon).isEqualTo(AllIcons.General.ArrowDown)
    trackModel.isCollapsed = true
    val expandedTrack = Track.create(trackModel, BooleanTrackRenderer())
    assertThat(expandedTrack.titleLabel.icon).isEqualTo(AllIcons.General.ArrowRight)
  }
}
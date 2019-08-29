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

import com.android.tools.adtui.model.trackgroup.TestTrackRendererType
import com.android.tools.adtui.model.trackgroup.TrackGroupModel
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackGroupTest {

  @Test
  fun createTrackGroup() {
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("Group").build()
    trackGroupModel.addTrackModel(TrackModel(true, TestTrackRendererType.FOO, "Foo"))
    trackGroupModel.addTrackModel(TrackModel("text", TestTrackRendererType.BAR, "Bar"))
    val trackGroup = TrackGroup(trackGroupModel, TestTrackRendererFactory())

    assertThat(trackGroup.titleLabel.text).isEqualTo("Group")
    assertThat(trackGroup.trackList.model.size).isEqualTo(2)
  }

  @Test
  fun collapseAndExpandTrackGroup() {
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("Group").setCollapsedInitially(true).build()
    val trackGroup = TrackGroup(trackGroupModel, TestTrackRendererFactory())

    assertThat(trackGroup.trackList.isVisible).isFalse()
    assertThat(trackGroup.collapseButton.text).isEqualTo("Expand Selection")

    trackGroup.setCollapsed(false)
    assertThat(trackGroup.trackList.isVisible).isTrue()
    assertThat(trackGroup.collapseButton.text).isNull()

    trackGroup.setCollapsed(true)
    assertThat(trackGroup.trackList.isVisible).isFalse()
    assertThat(trackGroup.collapseButton.text).isEqualTo("Expand Selection")
  }
}
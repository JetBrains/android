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
package com.android.tools.adtui.model.trackgroup

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class TrackGroupModelTest {

  @Test
  fun addTrackModel() {
    val trackGroupModel = TrackGroupModel.newBuilder().setTitle("group").build()
    trackGroupModel.addTrackModel(TrackModel.newBuilder(true, TestTrackRendererType.BOOLEAN, "foo"))
    trackGroupModel.addTrackModel(TrackModel.newBuilder("string", TestTrackRendererType.STRING, "bar"))

    val addedTrackModel1 = trackGroupModel.get(0)
    val addedTrackModel2 = trackGroupModel.get(1)
    assertThat(addedTrackModel1.title).isEqualTo("foo")
    assertThat(addedTrackModel1.dataModel).isEqualTo(true)
    assertThat(addedTrackModel2.title).isEqualTo("bar")
    assertThat(addedTrackModel2.dataModel).isEqualTo("string")
    assertWithMessage("Track IDs should be unique within a group").that(addedTrackModel1.id).isNotEqualTo(addedTrackModel2.id)
  }

  @Test
  fun `listeners run when display tag changed`() {
    var count1 = 0
    var count2 = 0
    val trackGroupModel = TrackGroupModel.newBuilder()
      .setTitle("group")
      .addDisplayToggle("Tag1", false) { ++count1 }
      .addDisplayToggle("Tag2", true) { ++count2 }
      .build()
    trackGroupModel.addTrackModel(TrackModel.newBuilder(true, TestTrackRendererType.BOOLEAN, "foo"))
    trackGroupModel.addTrackModel(TrackModel.newBuilder("string", TestTrackRendererType.STRING, "bar"))

    trackGroupModel.setDisplayTag("Tag1", false)
    assertThat(count1).isEqualTo(0)

    trackGroupModel.setDisplayTag("Tag1", true)
    assertThat(count1).isEqualTo(1)

    trackGroupModel.setDisplayTag("Tag1", false)
    assertThat(count1).isEqualTo(2)

    trackGroupModel.setDisplayTag("Tag2", false)
    assertThat(count2).isEqualTo(1)

    trackGroupModel.setDisplayTag("Tag2", true)
    assertThat(count2).isEqualTo(2)

    trackGroupModel.setDisplayTag("Tag2", true)
    assertThat(count2).isEqualTo(2)
  }
}
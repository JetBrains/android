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

class TrackGroupListModelTest {
  @Test
  fun addTrackGroupModel() {
    val trackGroupListModel = TrackGroupListModel()
    trackGroupListModel.addTrackGroupModel(TrackGroupModel.newBuilder().setTitle("group1"))
    trackGroupListModel.addTrackGroupModel(TrackGroupModel.newBuilder().setTitle("group2"))

    val addedTrackGroupModel1 = trackGroupListModel.get(0)
    val addedTrackGroupModel2 = trackGroupListModel.get(1)
    assertThat(addedTrackGroupModel1.title).isEqualTo("group1")
    assertThat(addedTrackGroupModel2.title).isEqualTo("group2")
    assertWithMessage("TrackGroup IDs should be unique within a group list")
      .that(addedTrackGroupModel1.id)
      .isNotEqualTo(addedTrackGroupModel2.id)
  }
}
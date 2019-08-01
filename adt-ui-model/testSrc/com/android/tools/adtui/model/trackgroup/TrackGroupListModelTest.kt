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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TrackGroupListModelTest {
  @Test
  fun addTrackGroupModel() {
    val trackGroupListModel = TrackGroupListModel()
    val trackGroupModel1 = TrackGroupModel("group1")
    val trackGroupModel2 = TrackGroupModel("group2")
    trackGroupListModel.addTrackGroupModel(trackGroupModel1)
    trackGroupListModel.addTrackGroupModel(trackGroupModel2)

    val addedTrackGroupModel1 = trackGroupListModel.get(0)
    val addedTrackGroupModel2 = trackGroupListModel.get(1)
    assertEquals(addedTrackGroupModel1.title, "group1")
    assertEquals(addedTrackGroupModel2.title, "group2")
    assertNotEquals("TrackGroup IDs should be unique within a group list", addedTrackGroupModel1.id, addedTrackGroupModel2.id)
  }
}
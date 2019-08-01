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

class TrackGroupModelTest {

  @Test
  fun addTrackModel() {
    val trackGroupModel = TrackGroupModel("group")
    val trackModel1 = TrackModel(123, TestTrackRendererType.FOO, "foo")
    val trackModel2 = TrackModel(321, TestTrackRendererType.BAR, "bar")
    trackGroupModel.addTrackModel(trackModel1)
    trackGroupModel.addTrackModel(trackModel2)

    val addedTrackModel1 = trackGroupModel.get(0)
    val addedTrackModel2 = trackGroupModel.get(1)
    assertEquals(addedTrackModel1.title, "foo")
    assertEquals(addedTrackModel1.dataModel, 123)
    assertEquals(addedTrackModel2.title, "bar")
    assertEquals(addedTrackModel2.dataModel, 321)
    assertNotEquals("Track IDs should be unique within a group", addedTrackModel1.id, addedTrackModel2.id)
  }
}
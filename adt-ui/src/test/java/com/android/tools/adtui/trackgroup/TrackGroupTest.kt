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

import com.android.tools.adtui.DragAndDropList
import com.android.tools.adtui.model.trackgroup.TestTrackRendererType
import com.android.tools.adtui.model.trackgroup.TrackGroupModel
import com.android.tools.adtui.model.trackgroup.TrackModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JList

class TrackGroupTest {

  @Test
  fun createTrackGroup() {
    val trackGroupModel = TrackGroupModel("Group")
    trackGroupModel.addTrackModel(TrackModel(true, TestTrackRendererType.FOO, "Foo"))
    trackGroupModel.addTrackModel(TrackModel("text", TestTrackRendererType.BAR, "Bar"))
    val trackGroup = TrackGroup(trackGroupModel, TestTrackRendererFactory())

    assertTrue(trackGroup.component is Container)
    val trackGroupComponent = trackGroup.component as Container
    assertEquals(trackGroupComponent.componentCount, 2)
    assertTrue(trackGroupComponent.getComponent(0) is JLabel)
    assertTrue(trackGroupComponent.getComponent(1) is JList<*>)
    assertEquals((trackGroupComponent.getComponent(1) as DragAndDropList<*>).model.size, 2)
  }
}
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
import com.android.tools.adtui.model.trackgroup.TrackModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.swing.JCheckBox
import javax.swing.JLabel

class TrackTest {

  @Test
  fun createTrack() {
    val trackModel = TrackModel.newBuilder (true, TestTrackRendererType.FOO, "foo").build()
    val trackComponent = Track.create(trackModel, FooTrackRenderer()).component
    assertThat(trackComponent.componentCount).isEqualTo(2)
    assertThat(trackComponent.getComponent(0)).isInstanceOf(JLabel::class.java)
    assertThat(trackComponent.getComponent(1)).isInstanceOf(JCheckBox::class.java)
  }

  @Test
  fun hideTrackHeader() {
    val trackModel = TrackModel.newBuilder(true, TestTrackRendererType.FOO, "foo").setHideHeader(true).build()
    val trackComponent = Track.create(trackModel, FooTrackRenderer()).component
    assertThat(trackComponent.componentCount).isEqualTo(1)
    assertThat(trackComponent.getComponent(0)).isInstanceOf(JCheckBox::class.java)
  }
}
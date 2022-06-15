/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorRotation
import org.junit.Test

class RotationStatisticsTest {

  @Test
  fun testStart() {
    val rotation = RotationStatistics()
    rotation.selectionMadeFromImage()
    rotation.selectionMadeFromComponentTree()
    rotation.start()
    val data = DynamicLayoutInspectorRotation.newBuilder()
    rotation.save { data }
    assertThat(data.imageClicksIn3D).isEqualTo(0)
    assertThat(data.imageClicksIn2D).isEqualTo(0)
    assertThat(data.componentTreeClicksIn3D).isEqualTo(0)
    assertThat(data.componentTreeClicksIn2D).isEqualTo(0)
  }

  @Test
  fun testToggleBackAndForth() {
    val rotation = RotationStatistics()
    rotation.start()

    rotation.selectionMadeFromComponentTree()
    rotation.selectionMadeFromComponentTree()
    rotation.selectionMadeFromImage()

    rotation.currentMode3D = true
    rotation.selectionMadeFromImage()
    rotation.selectionMadeFromImage()
    rotation.selectionMadeFromImage()

    rotation.currentMode3D = false
    rotation.selectionMadeFromImage()

    val data = DynamicLayoutInspectorRotation.newBuilder()
    rotation.save { data }
    assertThat(data.imageClicksIn3D).isEqualTo(3)
    assertThat(data.imageClicksIn2D).isEqualTo(2)
    assertThat(data.componentTreeClicksIn3D).isEqualTo(0)
    assertThat(data.componentTreeClicksIn2D).isEqualTo(2)
  }
}

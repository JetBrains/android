/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorCompose
import org.junit.Test

class ComposeStatisticsTest {
  @Test
  fun testStart() {
    val compose = ComposeStatistics()
    compose.gotoSourceFromPropertyValue(mock<ComposeViewNode>())
    compose.reflectionLibraryAvailable = false
    compose.selectionMadeFromComponentTree(mock<ComposeViewNode>())
    compose.selectionMadeFromComponentTree(mock<ComposeViewNode>())
    compose.selectionMadeFromImage(mock<ComposeViewNode>())
    compose.start()
    val data = DynamicLayoutInspectorCompose.newBuilder()
    compose.save(data)
    assertThat(data.kotlinReflectionAvailable).isTrue()
    assertThat(data.imageClicks).isEqualTo(0)
    assertThat(data.componentTreeClicks).isEqualTo(0)
    assertThat(data.goToSourceFromPropertyValueClicks).isEqualTo(0)
  }

  @Test
  fun testToggleBackAndForth() {
    val compose = ComposeStatistics()
    compose.start()

    compose.reflectionLibraryAvailable = false
    compose.gotoSourceFromPropertyValue(mock<ComposeViewNode>())
    compose.gotoSourceFromPropertyValue(mock<ComposeViewNode>())
    compose.gotoSourceFromPropertyValue(mock<ViewNode>())
    compose.selectionMadeFromComponentTree(mock<ComposeViewNode>())
    compose.selectionMadeFromComponentTree(mock<ComposeViewNode>())
    compose.selectionMadeFromComponentTree(mock<ViewNode>())
    compose.selectionMadeFromImage(mock<ComposeViewNode>())
    compose.selectionMadeFromImage(mock<ViewNode>())
    val data = DynamicLayoutInspectorCompose.newBuilder()
    compose.save(data)
    assertThat(data.kotlinReflectionAvailable).isFalse()
    assertThat(data.imageClicks).isEqualTo(1)
    assertThat(data.componentTreeClicks).isEqualTo(2)
    assertThat(data.goToSourceFromPropertyValueClicks).isEqualTo(2)
  }
}

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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.layoutinspector.InspectorClientProvider
import com.android.tools.idea.layoutinspector.LEGACY_DEVICE
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.LegacyClientProvider
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class LegacyClientTest {
  private val windowIds = mutableListOf<String>()
  private val legacyClientProvider = object : InspectorClientProvider {
    override fun create(params: InspectorClientLauncher.Params, inspector: LayoutInspector): InspectorClient {
      val loader = mock(LegacyTreeLoader::class.java)
      `when`(loader.getAllWindowIds(ArgumentMatchers.any())).thenReturn(windowIds)
      return LegacyClientProvider(loader).create(params, inspector) as LegacyClient
    }
  }

  @get:Rule
  val inspectorRule = LayoutInspectorRule(legacyClientProvider)

  @Test
  fun testReloadAllWindows() {
    windowIds.addAll(listOf("window1", "window2", "window3"))
    inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess() // This causes the tree to get loaded as a side effect
    val client = inspectorRule.inspectorClient as LegacyClient

    verify(client.treeLoader).loadComponentTree(argThat { event: LegacyEvent -> event.windowId == "window1" },
                                                any(ResourceLookup::class.java))
    verify(client.treeLoader).loadComponentTree(argThat { event: LegacyEvent -> event.windowId == "window2" },
                                                any(ResourceLookup::class.java))
    verify(client.treeLoader).loadComponentTree(argThat { event: LegacyEvent -> event.windowId == "window3" },
                                                any(ResourceLookup::class.java))
  }

  @Test
  fun testReloadAllWindowsWithNone() {
    inspectorRule.processes.selectedProcess = LEGACY_DEVICE.createProcess()
    val client = inspectorRule.inspectorClient as LegacyClient
    assertThat(client.reloadAllWindows()).isFalse()
  }
}
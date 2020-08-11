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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.ddmlib.testing.FakeAdbRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.PropertySetterRule
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class LegacyClientTest {
  @get:Rule
  val adbRule = FakeAdbRule()

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val clientFactoryRule = PropertySetterRule(
    { model, parentDisposable -> listOf(LegacyClient(model, parentDisposable)) },
    InspectorClient.Companion::clientFactory)

  @Test
  fun testReloadAllWindows() {
    val inspector = LayoutInspector(InspectorModel(projectRule.project), projectRule.fixture.projectDisposable)
    val client = inspector.allClients.firstIsInstance<LegacyClient>()

    val loader = mock(LegacyTreeLoader::class.java)
    `when`(loader.getAllWindowIds(ArgumentMatchers.any(), eq(client))).thenReturn(listOf("window1", "window2", "window3"))
    client.treeLoader = loader

    client.reloadAllWindows()
    verify(loader).loadComponentTree(argThat { event: LegacyEvent -> event.windowId == "window1" },
                                     any(ResourceLookup::class.java), eq(client), eq(projectRule.project))
    verify(loader).loadComponentTree(argThat { event: LegacyEvent -> event.windowId == "window2" },
                                     any(ResourceLookup::class.java), eq(client), eq(projectRule.project))
    verify(loader).loadComponentTree(argThat { event: LegacyEvent -> event.windowId == "window3" },
                                     any(ResourceLookup::class.java), eq(client), eq(projectRule.project))
  }

  @Test
  fun testReloadAllWindowsWithNone() {
    val inspector = LayoutInspector(InspectorModel(projectRule.project), projectRule.fixture.projectDisposable)
    val client = inspector.allClients.firstIsInstance<LegacyClient>()

    val loader = mock(LegacyTreeLoader::class.java)
    `when`(loader.getAllWindowIds(ArgumentMatchers.any(), eq(client))).thenReturn(emptyList())
    client.treeLoader = loader

    assertThat(client.reloadAllWindows()).isFalse()
  }
}
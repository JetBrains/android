/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.actions

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.TestComposePreviewManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.testFramework.TestActionEvent.createTestEvent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class ComposeShowFilterActionTest {

  @Rule @JvmField val rule = AndroidProjectRule.inMemory()

  @Test
  fun testShowFilter() {
    val surface = mock<DesignSurface<*>>()
    val manager = TestComposePreviewManager()
    whenever(surface.dataSnapshot(Mockito.any())).thenAnswer(object : Answer<Unit> {
      override fun answer(invocation: InvocationOnMock) {
        val sink = invocation.arguments[0] as DataSink
        sink[COMPOSE_PREVIEW_MANAGER] = manager
      }
    })
    manager.isFilterEnabled = false

    val action = ComposeShowFilterAction()
    action.actionPerformed(createTestEvent(CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT, surface)))

    assertTrue(manager.isFilterEnabled)
  }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.layout.manager

import androidx.compose.runtime.mutableStateOf
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.intellij.testFramework.ApplicationRule
import java.awt.Dimension
import java.awt.Point
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

class PositionableContentLayoutManagerTest {

  @get:Rule val projectRule = ApplicationRule()

  class TestLayoutManager : PositionableContentLayoutManager() {

    override fun layoutContainer(
      content: Collection<PositionableContent>,
      availableSize: Dimension,
    ) {}

    override fun preferredLayoutSize(
      content: Collection<PositionableContent>,
      availableSize: Dimension,
    ) = Dimension(300, 300)

    override fun getMeasuredPositionableContentPosition(
      content: Collection<PositionableContent>,
      availableWidth: Int,
      availableHeight: Int,
    ): Map<PositionableContent, Point> = emptyMap()
  }

  @Test
  fun relayoutHappened(): Unit = runBlocking {
    val relayoutCount = mutableStateOf(0)
    val layoutManager = TestLayoutManager()
    val panel = JPanel(layoutManager)
    val ui = FakeUi(panel)
    panel.revalidate()
    val job = launch { layoutManager.layoutContainerFlow.collect { relayoutCount.value++ } }

    // With or without revalidate
    panel.revalidate()
    withContext(uiThread) { ui.layoutAndDispatchEvents() }
    delay(100)
    assertEquals(1, relayoutCount.value)

    withContext(uiThread) { ui.layoutAndDispatchEvents() }
    delay(100)
    assertEquals(2, relayoutCount.value)

    job.cancel()
  }
}

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
package com.android.tools.idea.common.surface.sceneview

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.model.DisplaySettings
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.intellij.testFramework.ApplicationRule
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class InteractiveLabelPanelTest {

  @get:Rule val projectRule = ApplicationRule()
  private lateinit var scope: CoroutineScope

  @Before
  fun setUp() {
    scope = CoroutineScope(CoroutineName(javaClass.simpleName))
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun clickLabel() = runBlocking {
    val clickCount = CompletableDeferred<Unit>()
    fun labelClicked(): Boolean {
      clickCount.complete(Unit)
      return false
    }

    val settings =
      DisplaySettings().apply {
        setDisplayName("Name")
        setTooltip("Tooltip")
      }

    val label =
      InteractiveLabelPanel(settings, scope, MutableStateFlow(false), ::labelClicked).apply {
        size = Dimension(250, 50)
      }
    val ui = FakeUi(label)
    withContext(uiThread) { ui.clickOn(label) }
    withTimeout(TimeUnit.SECONDS.toMillis(1)) { clickCount.await() }
  }
}

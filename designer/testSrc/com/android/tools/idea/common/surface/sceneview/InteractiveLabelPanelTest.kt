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
import com.intellij.testFramework.ApplicationRule
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Ignore
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
  @Ignore("b/301927653")
  fun `click label`() {
    runBlocking {
      var clickCount = 0
      fun labelClicked(): Boolean {
        clickCount++
        return false
      }

      val displayName = MutableStateFlow("Name")
      val tooltip = MutableStateFlow("Tooltip")

      val label =
        InteractiveLabelPanel(displayName, tooltip, scope, ::labelClicked).apply {
          size = Dimension(250, 50)
        }
      FakeUi(label).also { it.clickOn(label) }
      withTimeout(TimeUnit.SECONDS.toMillis(5)) { assertEquals(1, clickCount) }
    }
  }
}

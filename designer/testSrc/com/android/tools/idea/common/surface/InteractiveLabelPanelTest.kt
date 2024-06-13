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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.swing.FakeUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class InteractiveLabelPanelTest {

  @get:Rule val projectRule = ApplicationRule()
  private lateinit var parentDisposable: Disposable

  @Before
  fun setUp() {
    parentDisposable = Disposer.newDisposable()
  }

  @After
  fun tearDown() {
    Disposer.dispose(parentDisposable)
  }

  @Test
  @Ignore("b/289994157")
  fun `click label`() {
    runBlocking {
      var clickCount = 0
      fun labelClicked(): Boolean {
        clickCount++
        return false
      }

      val layoutData = LayoutData(1.0, "Name", "Tooltip", 0, 0, Dimension(10, 10))
      val label =
        InteractiveLabelPanel(layoutData, parentDisposable, ::labelClicked).apply {
          size = Dimension(250, 50)
        }
      FakeUi(label).also { it.clickOn(label) }
      withTimeout(TimeUnit.SECONDS.toMillis(1)) { assertEquals(1, clickCount) }
    }
  }
}

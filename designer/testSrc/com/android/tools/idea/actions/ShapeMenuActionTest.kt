/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.configurations.AdaptiveIconShape
import com.android.tools.configurations.Configuration
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

class ShapeMenuActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testUpdateActions() {
    val configuration = Mockito.mock(Configuration::class.java)
    whenever(configuration.adaptiveShape).thenReturn(AdaptiveIconShape.SQUIRCLE)
    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(configuration))
    val menuAction = ShapeMenuAction()
    menuAction.updateActions(dataContext)

    val expected =
      """Squircle
    Square
    Circle
    Rounded Corners
    ✔ Squircle
"""

    val actual = prettyPrintActions(menuAction, dataContext = dataContext)
    assertThat(actual).isEqualTo(expected)
  }
}

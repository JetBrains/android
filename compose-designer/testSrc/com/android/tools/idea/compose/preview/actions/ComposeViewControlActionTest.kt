/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class ComposeViewControlActionTest {

  @JvmField @Rule val rule = AndroidProjectRule.inMemory().onEdt()

  val filterActionSuffix =
    """
    ------------------------------------------------------
    Filter Previews
  """
      .trim { it.isWhitespace() }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_VIEW_FILTER.clearOverride()
  }

  @Test
  fun testFilterNotVisibleWhenFlagDisabled() {
    StudioFlags.COMPOSE_VIEW_FILTER.override(false)
    val viewControlAction = ComposeViewControlAction()
    val dataContext = SimpleDataContext.getSimpleContext(DESIGN_SURFACE, mock<NlDesignSurface>())
    val actionContent =
      prettyPrintActions(viewControlAction, dataContext = dataContext).trim { it.isWhitespace() }
    assertFalse(actionContent.endsWith(filterActionSuffix))
  }

  @Test
  fun testFilterVisibleWhenFlagEnabled() {
    StudioFlags.COMPOSE_VIEW_FILTER.override(true)
    val viewControlAction = ComposeViewControlAction()
    val dataContext = SimpleDataContext.getSimpleContext(DESIGN_SURFACE, mock<NlDesignSurface>())
    val actionContent =
      prettyPrintActions(viewControlAction, dataContext = dataContext).trim { it.isWhitespace() }
    assertTrue(actionContent.endsWith(filterActionSuffix))
  }
}

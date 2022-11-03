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
package com.android.tools.idea.uibuilder.statelist

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.actions.ANIMATION_TOOLBAR
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.editor.AnimatedSelectorToolbar
import com.android.tools.idea.uibuilder.editor.AnimationToolbar
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectorMenuActionTest {

  @Rule
  @JvmField
  val rule = AndroidProjectRule.inMemory()

  @Test
  fun testShowWhenAnimatedSelectorToolbarIsNotSelectingTransition() {
    // The design surface is used to preview <animated-selector> file which is not selecting a transition.
    // Not selecting a transition means it is previewing the state as same as a selector. It should show the menu in this case.
    val action = SelectorMenuAction()
    val surface = NlDesignSurface.builder(rule.project, rule.testRootDisposable).build()
    val toolbar = mock<AnimatedSelectorToolbar>()
    DataManager.registerDataProvider(surface) { if (it == ANIMATION_TOOLBAR.name) toolbar else null }
    whenever(toolbar.isTransitionSelected()).thenReturn(false)

    val context = createContext(surface, toolbar)
    val presentation = PresentationFactory().getPresentation(action)
    val event = AnActionEvent.createFromDataContext("", presentation, context)
    action.update(event)

    assertTrue { presentation.isEnabledAndVisible }
    assertNull(presentation.description)
  }

  @Test
  fun testHideWhenAnimatedSelectorToolbarIsSelectingTransition() {
    // The design surface is used to preview <animated-selector> file which is selecting a transition. It shouldn't show the menu.
    val action = SelectorMenuAction()
    val surface = NlDesignSurface.builder(rule.project, rule.testRootDisposable).build()
    val toolbar = mock<AnimatedSelectorToolbar>()
    DataManager.registerDataProvider(surface) { if (it == ANIMATION_TOOLBAR.name) toolbar else null }
    whenever(toolbar.isTransitionSelected()).thenReturn(true)

    val context = createContext(surface, toolbar)
    val presentation = PresentationFactory().getPresentation(action)
    val event = AnActionEvent.createFromDataContext("", presentation, context)
    action.update(event)

    assertFalse { presentation.isEnabled }
    assertTrue { presentation.isVisible }
    assertEquals("Cannot select the state when previewing a transition", presentation.description)
  }

  @Test
  fun testHideWhenShowingAnimationToolbar() {
    // The design surface is used to preview <animated-vector> file. It shouldn't show the menu.
    val action = SelectorMenuAction()
    val surface = NlDesignSurface.builder(rule.project, rule.testRootDisposable).build()
    val toolbar = mock<AnimationToolbar>()
    DataManager.registerDataProvider(surface) { if (it == ANIMATION_TOOLBAR.name) toolbar else null }

    val context = createContext(surface, toolbar)
    val presentation = PresentationFactory().getPresentation(action)
    val event = AnActionEvent.createFromDataContext("", presentation, context)
    action.update(event)

    assertFalse { presentation.isEnabledAndVisible }
    assertNull(presentation.description)
  }

  @Test
  fun testShowWithoutAnimationToolbar() {
    // The design surface is used to preview <selector> file. It should show the menu,
    val action = SelectorMenuAction()
    val surface = NlDesignSurface.builder(rule.project, rule.testRootDisposable).build()

    val context = createContext(surface, null)
    val presentation = PresentationFactory().getPresentation(action)
    val event = AnActionEvent.createFromDataContext("", presentation, context)
    action.update(event)

    assertTrue(presentation.isEnabledAndVisible)
    assertNull(presentation.description)
  }

  @Test
  fun testNotShowingWithoutDesignSurface() {
    // The menu never shows outside a design surface.
    val action = SelectorMenuAction()

    val context = DataContext { dataId -> if (DESIGN_SURFACE.`is`(dataId)) null else null }
    val presentation = PresentationFactory().getPresentation(action)
    val event = AnActionEvent.createFromDataContext("", presentation, context)
    action.update(event)

    assertFalse(presentation.isEnabledAndVisible)
    assertNull(presentation.description)
  }

  private fun createContext(surface: DesignSurface<*>, toolbar: JPanel?): DataContext {
    return DataContext { dataId ->
      when {
        DESIGN_SURFACE.`is`(dataId) -> surface
        ANIMATION_TOOLBAR.`is`(dataId) -> toolbar
        else -> null
      }
    }
  }
}

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

import com.android.tools.idea.actions.ANIMATION_TOOLBAR
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.editor.AnimatedSelectorToolbar
import com.android.tools.idea.uibuilder.editor.AnimationToolbar
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent.createEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SelectorMenuActionTest {

  @Rule @JvmField val rule = AndroidProjectRule.inMemory()

  @Test
  fun testShowWhenAnimatedSelectorToolbarIsNotSelectingTransition() {
    // The design surface is used to preview <animated-selector> file which is not selecting a
    // transition.
    // Not selecting a transition means it is previewing the state as same as a selector. It should
    // show the menu in this case.
    val action = SelectorMenuAction()
    val surface = NlSurfaceBuilder.builder(rule.project, rule.testRootDisposable).build()
    val toolbar = mock<AnimatedSelectorToolbar>()
    DataManager.registerDataProvider(surface) {
      if (it == ANIMATION_TOOLBAR.name) toolbar else null
    }
    whenever(toolbar.isTransitionSelected()).thenReturn(false)

    val context = createContext(surface, toolbar)
    val presentation = PresentationFactory().getPresentation(action)
    val event = createEvent(context, presentation, "", ActionUiKind.NONE, null)
    action.update(event)

    assertTrue(presentation.isEnabledAndVisible)
    assertNull(presentation.description)
  }

  @Test
  fun testHideWhenAnimatedSelectorToolbarIsSelectingTransition() {
    // The design surface is used to preview <animated-selector> file which is selecting a
    // transition. It shouldn't show the menu.
    val action = SelectorMenuAction()
    val surface = NlSurfaceBuilder.builder(rule.project, rule.testRootDisposable).build()
    val toolbar = mock<AnimatedSelectorToolbar>()
    DataManager.registerDataProvider(surface) {
      if (it == ANIMATION_TOOLBAR.name) toolbar else null
    }
    whenever(toolbar.isTransitionSelected()).thenReturn(true)

    val context = createContext(surface, toolbar)
    val presentation = PresentationFactory().getPresentation(action)
    val event = createEvent(context, presentation, "", ActionUiKind.NONE, null)
    action.update(event)

    assertFalse(presentation.isEnabled)
    assertTrue(presentation.isVisible)
    assertEquals("Cannot select the state when previewing a transition", presentation.description)
  }

  @Test
  fun testHideWhenShowingAnimationToolbar() {
    // The design surface is used to preview <animated-vector> file. It shouldn't show the menu.
    val action = SelectorMenuAction()
    val surface = NlSurfaceBuilder.builder(rule.project, rule.testRootDisposable).build()
    val toolbar = mock<AnimationToolbar>()
    DataManager.registerDataProvider(surface) {
      if (it == ANIMATION_TOOLBAR.name) toolbar else null
    }

    val context = createContext(surface, toolbar)
    val presentation = PresentationFactory().getPresentation(action)
    val event = createEvent(context, presentation, "", ActionUiKind.NONE, null)
    action.update(event)

    assertFalse(presentation.isEnabledAndVisible)
    assertNull(presentation.description)
  }

  @Test
  fun testShowWithoutAnimationToolbar() {
    // The design surface is used to preview <selector> file. It should show the menu,
    val action = SelectorMenuAction()
    val surface = NlSurfaceBuilder.builder(rule.project, rule.testRootDisposable).build()

    val context = createContext(surface, null)
    val presentation = PresentationFactory().getPresentation(action)
    val event = createEvent(context, presentation, "", ActionUiKind.NONE, null)
    action.update(event)

    assertTrue(presentation.isEnabledAndVisible)
    assertNull(presentation.description)
  }

  @Test
  fun testNotShowingWithoutDesignSurface() {
    // The menu never shows outside a design surface.
    val action = SelectorMenuAction()

    val context = DataContext.EMPTY_CONTEXT
    val presentation = PresentationFactory().getPresentation(action)
    val event = createEvent(context, presentation, "", ActionUiKind.NONE, null)
    action.update(event)

    assertFalse(presentation.isEnabledAndVisible)
    assertNull(presentation.description)
  }

  private fun createContext(surface: DesignSurface<*>, toolbar: JPanel?): DataContext =
    SimpleDataContext.builder()
      .add(DESIGN_SURFACE, surface)
      .apply { (toolbar as? AnimationToolbar)?.let { add(ANIMATION_TOOLBAR, it) } }
      .build()
}

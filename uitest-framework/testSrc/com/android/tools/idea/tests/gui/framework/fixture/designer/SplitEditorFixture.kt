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
package com.android.tools.idea.tests.gui.framework.fixture.designer

import com.android.tools.adtui.TextAccessors
import com.android.tools.idea.common.editor.SplitEditor
import com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.WorkBenchFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlDesignSurfaceFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.base.Preconditions.checkState
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import icons.StudioIcons
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiQuery
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.fixture.JPopupMenuFixture
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Wait
import javax.swing.JComponent

/**
 * Fixture wrapping for [SplitEditor]
 */
class SplitEditorFixture(val robot: Robot, val editor: SplitEditor<out FileEditor>) :
  ComponentFixture<SplitEditorFixture, JComponent>(SplitEditorFixture::class.java, robot, editor.component) {
  private fun setMode(modeName: String) = findActionButtonByText(modeName).click()

  fun setCodeMode() = setMode("Code")
  fun setSplitMode() = setMode("Split")
  fun setDesignMode() = setMode("Design")

  fun setRepresentation(name: String) {
    val representationSelector = ActionButtonFixture.findByIcon(StudioIcons.LayoutEditor.Palette.LIST_VIEW, robot, target())
      .waitUntilEnabledAndShowing()

    if (name == TextAccessors.getTextAccessor(representationSelector.target())?.text) return

    representationSelector.click()
    JPopupMenuFixture(robot, robot.findActivePopupMenu()!!)
      .menuItem(object: GenericTypeMatcher<ActionMenuItem>(ActionMenuItem::class.java) {
        override fun isMatching(component: ActionMenuItem): Boolean =component.text == name
      })
      .click()
  }

  val designSurface: NlDesignSurfaceFixture
    get() {
      val surface = waitUntilShowing(robot, Matchers.byType(NlDesignSurface::class.java))
      return NlDesignSurfaceFixture(robot, surface)
    }

  /**
   * Returns whether the [NlDesignSurface] is showing in the split editor or not.
   */
  private val hasDesignSurface: Boolean
    get() =
      try {
        waitUntilShowing(robot, null, Matchers.byType(NlDesignSurface::class.java), 2)
        true
      }
      catch (_: WaitTimedOutError) {
        false
      }

  private val workbenchPanel: WorkBenchFixture
    get() = WorkBenchFixture.findShowing(target(), robot)

  fun waitForRenderToFinish(wait: Wait = Wait.seconds(20)): SplitEditorFixture {
    wait.expecting("WorkBench to show content").until {
      try {
        workbenchPanel.isShowingContent()
      }
      catch (e: ComponentLookupException) {
        Logger.getInstance(SplitEditorFixture::class.java).info("Failed to get workbenchPanel, will retry until success or timeout", e)
        return@until false
      }
    }
    // Fade out of the loading panel takes 500ms
    Pause.pause(1000)
    wait.expecting("Surface to be ready").until { hasDesignSurface }
    designSurface.waitForRenderToFinish(wait)
    return this
  }

  fun waitForSceneViewsCount(count: Int, wait: Wait = Wait.seconds(10)): SplitEditorFixture {
    wait.expecting("Expecting $count SceneView(s) to appear").until { designSurface.allSceneViews.size == count }
    return this
  }

  fun hasRenderErrors() = designSurface.hasRenderErrors()

  fun findActionButtonByText(text: String): ActionButtonFixture {
    val button = waitUntilShowing(
      robot(), target(), object : GenericTypeMatcher<ActionButton>(ActionButton::class.java) {
      override fun isMatching(component: ActionButton): Boolean {
        return text == component.action.templateText ||
               (component.presentation.description?.startsWith(text) ?: false)
      }
    })
    return ActionButtonFixture(robot(), button)
  }

}

/**
 * Returns the [SplitEditorFixture] from a given [EditorFixture]. If this method is called on an editor that is not a split editor,
 * the method will throw an exception.
 */
fun EditorFixture.getSplitEditorFixture(): SplitEditorFixture {
  // Wait for the editor to do any initializations
  ideFrame.robot().waitForIdle()

  return GuiQuery.getNonNull {
    val editors = FileEditorManager.getInstance(ideFrame.project).selectedEditors
    checkState(editors.isNotEmpty(), "no selected editors")
    val selected = editors[0]
    checkState(selected is SplitEditor<out FileEditor>, "invalid editor selected")
    SplitEditorFixture(ideFrame.robot(), selected as SplitEditor<out FileEditor>)
  }
}

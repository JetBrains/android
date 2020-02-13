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

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.SplitEditor
import com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.WorkBenchLoadingPanelFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlDesignSurfaceFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.base.Preconditions.checkState
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiQuery
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Wait
import javax.swing.JComponent

/**
 * Fixture wrapping for [SplitEditor]
 */
class SplitEditorFixture(val robot: Robot, val editor: SplitEditor<out FileEditor>) :
  ComponentFixture<SplitEditorFixture, JComponent>(SplitEditorFixture::class.java, robot, editor.component) {
  private fun setMode(modeName: String) = robot.click(robot.finder().findByName(target(), modeName))

  fun setCodeMode() = setMode("Code")
  fun setSplitMode() = setMode("Split")
  fun setDesignMode() = setMode("Design")


  val designSurface: NlDesignSurfaceFixture by lazy {
    val surface = waitUntilShowing(robot, Matchers.byType(NlDesignSurface::class.java))
    NlDesignSurfaceFixture(robot, surface)
  }

  private val loadingPanel: WorkBenchLoadingPanelFixture by lazy {
    val workbench: WorkBench<*> = robot.finder().findByType(target(), WorkBench::class.java, false)
    WorkBenchLoadingPanelFixture(robot, workbench.loadingPanel)
  }

  fun waitForRenderToFinish(wait: Wait = Wait.seconds(10)): SplitEditorFixture {
    designSurface.waitForRenderToFinish(wait)
    wait.expecting("WorkBench is showing").until { !loadingPanel.isLoading() }
    // Fade out of the loading panel takes 500ms
    Pause.pause(1000)
    return this
  }

  fun hasRenderErrors() = designSurface.hasRenderErrors()


  fun findActionButtonByText(text: String): ActionButtonFixture {
    val button = waitUntilShowing(
      robot(), target(), object : GenericTypeMatcher<ActionButton>(ActionButton::class.java) {
      override fun isMatching(component: ActionButton): Boolean {
        return text == component.action.templateText
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
// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.adtui.HtmlLabel
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.IdeFrameContainerFixture
import com.android.tools.idea.tests.gui.framework.find
import com.android.tools.idea.tests.gui.framework.matcher
import com.android.tools.idea.tests.gui.framework.robot
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.components.JBList
import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.util.ToolkitProvider
import sun.awt.SunToolkit
import java.awt.Container

private const val WAIT_FOR_IDLE_TIMEOUT_MS: Int = 20_000

fun HtmlLabel.plainText(): String = document.getText(0, document.length)

fun waitForIdle() {
  val start = System.currentTimeMillis()
  while (System.currentTimeMillis() - start < WAIT_FOR_IDLE_TIMEOUT_MS) {
    try {
      (ToolkitProvider.instance().defaultToolkit() as SunToolkit).realSync()
      return
    }
    catch (_: SunToolkit.InfiniteLoop) {
      // The implementation of SunToolkit.realSync() allows up to 20 events to be processed in a batch.
      // We often have more than 20 events primarily caused by invokeLater() invocations.
    }
  }
  val details = try {
    buildString {
      appendln("EventCount: ${IdeEventQueue.getInstance().eventCount}")
      appendln("TrueCurrentEvent: ${IdeEventQueue.getInstance().trueCurrentEvent}")
    }
  }
  catch (t: Throwable) {
    t.message.orEmpty()
  }
  throw WaitTimedOutError("Timed out waiting for idle: $details")
}

internal fun IdeFrameContainerFixture.clickToolButton(titlePrefix: String) {
  fun ActionButton.matches() = toolTipText?.startsWith(titlePrefix) ?: false
  // Find the topmost tool button. (List/Map editors may contains similar buttons)
  val button =
    robot()
      .finder()
      .findAll(container, matcher<ActionButton> { it.matches() })
      .minBy { button -> generateSequence<Container>(button) { it.parent }.count() }
    ?: robot().finder().find<ActionButton>(container) { it.matches() }
  robot().click(button)
}

/**
 * Returns the popup list being displayed, assuming there is one and it is the only one.
 */
internal fun IdeFrameContainerFixture.getList(): JBList<*> {
  return GuiTests.waitUntilShowingAndEnabled<JBList<*>>(robot(), null, object : GenericTypeMatcher<JBList<*>>(JBList::class.java) {
    override fun isMatching(list: JBList<*>): Boolean {
      return list.javaClass.name == "com.intellij.ui.popup.list.ListPopupImpl\$MyList"
    }
  })
}

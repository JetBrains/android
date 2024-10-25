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
package com.android.tools.idea.wearwhs.view

import com.android.testutils.waitForCondition
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.testing.ui.FakeActionPopupMenu
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.view.WearHealthServicesStateManagerTest.Companion.TEST_MAX_WAIT_TIME_SECONDS
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.TestActionEvent.createTestEvent
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertNotNull

internal suspend fun <T> StateFlow<T>.waitForValue(
  value: T,
  timeoutSeconds: Long = TEST_MAX_WAIT_TIME_SECONDS,
) {
  awaitStatus("Timeout waiting for value $value", timeoutSeconds.seconds) { it == value }
}

internal fun FakeUi.clickOnApplyButton() {
  val applyButton = waitForDescendant<JButton> { it.text == message("wear.whs.panel.apply") }
  applyButton.doClick()
}

// The UI loads on asynchronous coroutine, we need to wait
internal inline fun <reified T> FakeUi.waitForDescendant(
  crossinline predicate: (T) -> Boolean = { true }
): T {
  waitForCondition(WearHealthServicesPanelTest.TEST_MAX_WAIT_TIME_SECONDS, TimeUnit.SECONDS) {
    root.findDescendant(predicate) != null
  }
  return root.findDescendant(predicate)!!
}

internal fun FakeUi.triggerEventsButton() =
  waitForDescendant<ActionButton> { it.icon == AllIcons.Actions.More }

internal fun FakeUi.clickOnTriggerEvent(
  fakePopupProvider: () -> FakeActionPopupMenu,
  eventName: String? = null,
) {
  val triggerEventsButton = triggerEventsButton()
  triggerEventsButton.click()

  val triggerEventActions =
    fakePopupProvider().getActions().flatMap {
      (it as? DropDownAction)?.childActionsOrStubs?.toList() ?: emptyList()
    }

  val triggerEventAction =
    triggerEventActions.firstOrNull { eventName == null || it.templateText == eventName }
  assertNotNull(
    "An event trigger action${eventName?.let { " with name $eventName " }} was expected",
    triggerEventAction,
  )

  triggerEventAction!!.actionPerformed(createTestEvent())
}

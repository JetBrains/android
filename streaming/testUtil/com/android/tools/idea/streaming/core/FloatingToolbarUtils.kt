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
package com.android.tools.idea.streaming.core

import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.flags.StudioFlags
import kotlin.time.Duration.Companion.seconds

/** Expands the floating toolbar if it is not expanded already. */
fun FakeUi.expandFloatingToolbar() {
  layoutAndDispatchEvents()
  val toolbar = getComponent<FloatingToolbarContainer>()
  if (toolbar.activationFactor != 1.0) {
    // Trigger expansion of the floating toolbar.
    if (StudioFlags.RUNNING_DEVICES_COLLAPSIBLE_FLOATING_TOOLBARS.get()) {
      mouse.click(toolbar.locationOnScreen.x + toolbar.width - toolbar.height / 2, toolbar.locationOnScreen.y + toolbar.height / 2)
    }
    else {
      mouse.moveTo(toolbar.locationOnScreen.x + toolbar.width / 2, toolbar.locationOnScreen.y + toolbar.height - toolbar.width / 2)
    }
    layoutAndDispatchEvents()
    waitForCondition(1.seconds) { toolbar.activationFactor == 1.0 }
    layoutAndDispatchEvents()
  }
}

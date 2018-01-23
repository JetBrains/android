/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.bazel

import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.timing.Wait
import java.awt.event.InputEvent
import java.awt.event.KeyEvent


fun IdeSettingsDialogFixture.selectBazelSettings(): IdeSettingsDialogFixture {
  return selectPath("Bazel Settings")
}

fun IdeSettingsDialogFixture.setBazelBinaryPathField(path: String): IdeSettingsDialogFixture {
  Wait.seconds(1).expecting("Bazel binary location text field to be visible")
    .until {
      try {
        val found = robot().finder().findByLabel("Bazel binary location")
        robot().click(found)
        true
      } catch (e: ComponentLookupException) {
        false
      }
    }

  // Select all text with Ctrl-A and then enter text to make sure the old text is replaced.
  robot().pressAndReleaseKey(KeyEvent.VK_A, InputEvent.CTRL_MASK)
  robot().enterText(path)
  return this
}
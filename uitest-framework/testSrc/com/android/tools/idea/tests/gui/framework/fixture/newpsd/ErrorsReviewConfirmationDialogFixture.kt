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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.tests.gui.framework.DialogContainerFixture
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import org.fest.swing.core.Robot
import javax.swing.JDialog

class ErrorsReviewConfirmationDialogFixture(
  val container: JDialog,
  val robot: Robot
) : DialogContainerFixture {

  override fun target(): JDialog = container
  override fun robot(): Robot = robot
  override fun maybeRestoreLostFocus() = Unit

  fun clickReview() = clickButtonAndWaitDialogDisappear("Review")

  fun clickIgnore() = clickButtonAndWaitDialogDisappear("Ignore and Apply")

  companion object {
    fun find(robot: Robot, title: String): ErrorsReviewConfirmationDialogFixture {
      val dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog::class.java, title))
      return ErrorsReviewConfirmationDialogFixture(dialog, robot)
    }
  }
}


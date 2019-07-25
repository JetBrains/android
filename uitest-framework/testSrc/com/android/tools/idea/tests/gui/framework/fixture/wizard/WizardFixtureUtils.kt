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
package com.android.tools.idea.tests.gui.framework.fixture.wizard

import org.fest.swing.core.GenericTypeMatcher
import org.fest.swing.core.Robot
import org.fest.swing.finder.WindowFinder
import javax.swing.JDialog

fun findMenuDialog(robot: Robot, targetTitle: String): JDialog {
  return WindowFinder.findDialog(object : GenericTypeMatcher<JDialog>(JDialog::class.java) {
    override fun isMatching(dialog: JDialog) = dialog.title == targetTitle && dialog.isShowing
  }).withTimeout(500).using(robot).target() as JDialog
}
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
package com.android.tools.idea.tests.gui.framework.fixture

import com.android.tools.adtui.workbench.WorkBenchLoadingPanel
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import org.fest.swing.core.Robot

class WorkBenchLoadingPanelFixture(robot: Robot, loadingPanel: WorkBenchLoadingPanel) :
  ComponentFixture<WorkBenchLoadingPanelFixture, WorkBenchLoadingPanel>(WorkBenchLoadingPanelFixture::class.java, robot, loadingPanel) {

  fun isLoading() = target().isLoading

  fun hasError() = target().hasError()

  companion object {
    fun find(robot: Robot) =
      WorkBenchLoadingPanelFixture(robot, robot.finder().find(Matchers.byType(WorkBenchLoadingPanel::class.java).andIsShowing()))
  }
}
/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.adtui.workbench.WorkBenchLoadingPanel
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import org.fest.swing.core.Robot
import java.awt.Container

class WorkBenchFixture(robot: Robot, workbench: WorkBench<*>) :
  ComponentFixture<WorkBenchFixture, WorkBench<*>>(WorkBenchFixture::class.java, robot, workbench) {

  fun isLoading() = target().loadingPanel.isLoading
  fun isShowingContent() = target().isShowingContent

  companion object {
    fun findShowing(root: Container, robot: Robot) =
      WorkBenchFixture(robot, robot.finder().find(root, Matchers.byType(WorkBench::class.java).andIsShowing()))
  }
}
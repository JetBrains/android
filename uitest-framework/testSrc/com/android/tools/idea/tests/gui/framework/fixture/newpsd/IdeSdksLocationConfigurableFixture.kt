/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.IdeSdksConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.HyperlinkLabelFixture
import com.intellij.ui.HyperlinkLabel
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import java.awt.Container

class IdeSdksLocationConfigurableFixture(
  val robot: Robot,
  val container: Container
) : ContainerFixture<Container> {
  override fun target(): Container = container
  override fun robot(): Robot = robot

  private fun findGradleSettings() : HyperlinkLabelFixture{
    return HyperlinkLabelFixture(robot, robot.finder()
      .find(target()) { it is HyperlinkLabel && it.text.contains("Gradle Settings") } as HyperlinkLabel)
  }

  fun clickGradleSetting(){
    // Click's exactly on the hyperlink to open the gradle settle dialog.
    findGradleSettings().clickLink("Gradle Settings.")
  }

}

fun ProjectStructureDialogFixture.selectIdeSdksLocationConfigurable(): IdeSdksLocationConfigurableFixture {
  selectConfigurable("SDK Location")
  return IdeSdksLocationConfigurableFixture(
    robot(),
    findConfigurable(IdeSdksConfigurable.IDE_SDKS_LOCATION_VIEW))
}

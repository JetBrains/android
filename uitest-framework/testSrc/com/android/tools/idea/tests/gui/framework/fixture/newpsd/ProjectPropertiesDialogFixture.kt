/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.configurables.PROJECT_VIEW
import org.fest.swing.core.Robot
import org.fest.swing.fixture.ContainerFixture
import java.awt.Container

class ProjectPropertiesDialogFixture(
val robot: Robot,
val container: Container
) : ConfigPanelFixture() {
  override fun target(): Container = container
  override fun robot(): Robot = robot
  fun findPluginVersionEditor(): PropertyEditorFixture = findEditor("Android Gradle Plugin Version")

}
fun ProjectStructureDialogFixture.selectProject(): ProjectPropertiesDialogFixture {
  selectConfigurable("Project")
  return ProjectPropertiesDialogFixture(
    robot(),
    findConfigurable(PROJECT_VIEW))
}
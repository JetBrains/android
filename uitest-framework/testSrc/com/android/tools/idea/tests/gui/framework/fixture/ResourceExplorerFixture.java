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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.resourceExplorer.editor.ResourceExplorer;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.icons.AllIcons;
import javax.swing.JPanel;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JPanelFixture;
import org.jetbrains.annotations.NotNull;

public class ResourceExplorerFixture extends JPanelFixture {

  @NotNull
  public static ResourceExplorerFixture find(@NotNull Robot robot) {
    ResourceExplorer explorer = GuiTests.waitUntilShowing(robot, Matchers.byType(ResourceExplorer.class));
    return new ResourceExplorerFixture(robot, explorer);
  }

  public ResourceExplorerFixture(@NotNull Robot robot, @NotNull JPanel target) {
    super(robot, target);
  }

  @NotNull
  public ResourceExplorerFixture clickAddButton() {
    ActionButtonFixture.findByIcon(AllIcons.General.Add, robot(), target()).click();
    return this;
  }
}

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
package com.android.tools.idea.tests.gui.framework.fixture.projectstructure;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DeveloperServicesFixture extends ProjectStructureDialogFixture {

  DeveloperServicesFixture(@NotNull JDialog dialog, @NotNull IdeFrameFixture ideFrameFixture) {
    super(dialog, ideFrameFixture);
  }

  public void toggleCheckBox() {
    JCheckBox checkBox = GuiTests.waitUntilShowingAndEnabled(robot(), this.target(), Matchers.byType(JCheckBox.class));
    new JCheckBoxFixture(robot(), checkBox).click();
  }
}

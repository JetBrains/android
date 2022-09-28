/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.table.TableView;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

public class DeviceManagerToolWindowFixture extends ToolWindowFixture {

  @NotNull
  private final IdeFrameFixture ideFrame;

  public DeviceManagerToolWindowFixture(@NotNull IdeFrameFixture projectFrame) {
    super("Device Manager", projectFrame.getProject(), projectFrame.robot());
    ideFrame = projectFrame;
    activate();
  }

  public AvdEditWizardFixture clickCreateDeviceButton() {
    JButton button = GuiTests.waitUntilShowing(robot(),
                                               Matchers.byText(JButton.class, "Create device").andIsEnabled());
    myRobot.click(button);
    return AvdEditWizardFixture.find(robot());
  }

}


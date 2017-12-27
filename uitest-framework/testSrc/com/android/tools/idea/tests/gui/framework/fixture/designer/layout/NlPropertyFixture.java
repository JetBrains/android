/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseResourceDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.fest.swing.core.Robot;

import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * Fixture for an individual property in the property inspector
 */
public class NlPropertyFixture {
  private final Robot myRobot;
  private final JPanel myValuePanel;

  public NlPropertyFixture(Robot robot, JPanel valuePanel) {
    myRobot = robot;
    myValuePanel = valuePanel;
  }

  public ChooseResourceDialogFixture clickCustomizer() {
    //JPanel
    //  TextEditor
    //  BrowsePanel
    //    ActionButton
    ActionButton button = GuiTests.waitUntilFound(myRobot, myValuePanel, Matchers.byType(ActionButton.class));
    new ActionButtonFixture(myRobot, button).click();
    return ChooseResourceDialogFixture.find(myRobot);
  }

  public String getValue() {
    JTextComponent editor = GuiTests.waitUntilFound(myRobot, myValuePanel, Matchers.byType(JTextComponent.class));
    return editor.getText();
  }
}

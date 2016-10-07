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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.intellij.designer.propertyTable.editors.TextEditor;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;

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

  public void clickCustomizer() {
    //JPanel
    //  TextEditor
    //  BrowsePanel
    //    ActionButton
    ActionButton button = waitUntilFound(myRobot, myValuePanel, new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton list) {
        return true;
      }
    });
    new ComponentDriver(myRobot).click(button);
  }

  public String getValue() {
    JTextComponent editor = waitUntilFound(myRobot, myValuePanel, new GenericTypeMatcher<JTextComponent>(JTextComponent.class) {
      @Override
      protected boolean isMatching(@NotNull JTextComponent textComponent) {
        return true;
      }
    });
    return editor.getText();
  }
}
